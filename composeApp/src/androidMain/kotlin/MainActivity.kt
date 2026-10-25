package com.linkkeeper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ui.TribbaeTheme
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import data.AndroidStorage
import data.ImagePickerHelper
import data.LinkRepository
import data.NotificationHelper
import data.OgImageFetcher
import data.extractUrl
import viewmodel.LinkViewModel

class MainActivity : ComponentActivity() {
    /** Contenu reçu d'une autre application (partage, lien de dossier), à ouvrir dans l'app. */
    private val incoming = mutableStateOf<SharedContent?>(null)

    /** Android 13+ : les rappels ne s'affichent qu'avec l'autorisation de notifier. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var notificationAsked = false

    /**
     * Demande l'autorisation d'afficher les notifications au moment où un rappel
     * est planifié (pas au lancement), une seule fois par ouverture de l'app :
     * si elle est refusée, Android ne la repropose plus après deux refus.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationAsked) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationAsked = true
        runOnUiThread { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Build de dev visant un backend local (vide : production)
        if (BuildConfig.API_URL.isNotBlank()) data.Backend.url = BuildConfig.API_URL
        // Avant super.onCreate : l'écran de lancement repasse ensuite au thème de l'app
        installLaunchTransition(launchedFromIcon = intent?.action == Intent.ACTION_MAIN)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createChannel(applicationContext)

        // ImagePickerHelper doit être créé avant setContent (enregistre les launchers)
        val imagePicker = ImagePickerHelper(this)

        val storage = AndroidStorage(applicationContext)
        ui.Appearance.pref = storage.loadAppearance()
        ui.Appearance.save = storage::saveAppearance
        val repository = LinkRepository(storage)
        // Une seule fois : à la recréation (rotation…), le partage est déjà ouvert
        if (savedInstanceState == null) incoming.value = parseIncoming(intent)
        val appContext = applicationContext

        setContent {
            val viewModel = remember {
                LinkViewModel(repository).also {
                    it.authToken = storage.loadToken()
                    it.ogImageFetcher = { url -> OgImageFetcher.fetch(url) }
                    it.localPreview = { url ->
                        OgImageFetcher.fetchPreview(url)
                            ?.takeIf { p -> p.title.isNotBlank() || p.images.isNotEmpty() }
                            ?.let { p -> data.LinkPreview(title = p.title, image = p.image.orEmpty(), images = p.images) }
                    }
                    it.reminderScheduler = { link ->
                        if (link.eventDate != null) {
                            ensureNotificationPermission()
                            NotificationHelper.scheduleReminder(
                                appContext, link.id, link.title, link.eventDate
                            )
                        }
                    }
                    it.reminderCanceller = { linkId ->
                        NotificationHelper.cancelReminder(appContext, linkId)
                    }
                    it.urlOpener = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(intent)
                    }
                    it.imagePickerGallery = { onResult -> imagePicker.pickFromGallery(onResult) }
                    it.imagePickerCamera = { onResult -> imagePicker.pickFromCamera(onResult) }
                    it.fetchMissingImages()
                }
            }
            // Apparence (« Ma tribu ») : jour, soir ou comme l'appareil
            ui.Appearance.systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = ui.Appearance.dark
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
            }
            TribbaeTheme {
                AppModern(vm = viewModel, incoming = incoming.value)
            }
        }
    }

    /**
     * Écran de lancement aux couleurs du logo A. Sur Android 12+, on laisse l'icône animée
     * (soleil levant, tipi, fanion) se jouer jusqu'au bout avant de partir ; puis le tipi
     * s'avance vers l'utilisateur pendant que le fond orange s'efface sur l'application.
     */
    /** Partage reçu alors que Tribbae est déjà ouvert au premier plan. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIncoming(intent)?.let { incoming.value = it }
    }

    private fun parseIncoming(intent: Intent?): SharedContent? {
        val (url, title) = extractShared(intent)
        val token = extractShareToken(intent)
        val inviteToken = extractInviteToken(intent)
        val resetToken = extractResetToken(intent)
        if (url == null && title == null && token == null && inviteToken == null && resetToken == null) return null
        return SharedContent(url = url, title = title, folderToken = token, inviteToken = inviteToken, resetToken = resetToken)
    }

    private fun installLaunchTransition(launchedFromIcon: Boolean) {
        val splash = installSplashScreen()
        // L'animation du logo ne se joue qu'à l'ouverture depuis l'icône : pour un
        // partage, on veut le formulaire tout de suite.
        if (launchedFromIcon && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val start = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - start < ICON_ANIMATION_MS }
        }
        splash.setOnExitAnimationListener { provider ->
            // Ouverte par une autre application (partage depuis Chrome, Instagram…),
            // l'app reçoit un écran de lancement SANS icône : provider.iconView lève
            // alors une NullPointerException (checkNotNull dans androidx). Ne jamais
            // supposer que l'icône existe, ni laisser l'animation faire planter l'app.
            val icon = runCatching { provider.iconView }.getOrNull()
            icon?.animate()
                ?.scaleX(1.5f)?.scaleY(1.5f)?.alpha(0f)
                ?.setDuration(EXIT_MS)
                ?.setInterpolator(AccelerateInterpolator())
                ?.start()
            runCatching {
                provider.view.animate()
                    .alpha(0f)
                    .setStartDelay(if (icon != null) EXIT_MS / 4 else 0)
                    .setDuration(EXIT_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { provider.remove() }
                    .start()
            }.onFailure { provider.remove() }
        }
    }

    /** Jeton d'un lien de partage de dossier ouvert dans l'app (https://…/share/<jeton>). */
    private fun extractShareToken(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val segments = intent.data?.pathSegments ?: return null
        return segments.takeIf { it.size == 2 && it[0] == "share" }?.get(1)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{16,64}")) }
    }

    /** Jeton d'un lien d'invitation dans une tribu (https://…/invitation#<jeton>). */
    private fun extractInviteToken(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (data.path?.trimEnd('/') != "/invitation") return null
        return data.fragment?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{20,128}")) }
    }

    /** Jeton du lien « nouveau mot de passe » d'un email (https://…/mot-de-passe/nouveau#<jeton>). */
    private fun extractResetToken(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (data.path?.trimEnd('/') != "/mot-de-passe/nouveau") return null
        return data.fragment?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{20,128}")) }
    }

    /**
     * Contenu partagé depuis une autre application : l'adresse (souvent noyée
     * dans un texte, « Regarde ça https://… ») et, sans adresse, le texte
     * lui-même qui devient le titre de l'idée.
     */
    private fun extractShared(intent: Intent?): Pair<String?, String?> {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null to null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val url = extractUrl(text) ?: extractUrl(subject)
        // Chrome envoie le titre de la page en sujet : il sert de titre à l'idée
        if (url != null) return url to subject.takeIf { it.isNotBlank() && extractUrl(it) == null }
        return null to (subject.ifBlank { text }.take(300).ifBlank { null })
    }

    private companion object {
        /** Durée de l'icône animée (splash_icon_animated.xml : le fanion finit de flotter à ~1 s). */
        const val ICON_ANIMATION_MS = 1000L
        const val EXIT_MS = 320L
    }
}
