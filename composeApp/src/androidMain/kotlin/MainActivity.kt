package com.linkkeeper.app

import android.content.Intent
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
import androidx.compose.runtime.remember
import data.AndroidStorage
import data.ImagePickerHelper
import data.LinkRepository
import data.NotificationHelper
import data.OgImageFetcher
import data.extractUrl
import viewmodel.LinkViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Avant super.onCreate : l'écran de lancement repasse ensuite au thème de l'app
        installLaunchTransition()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createChannel(applicationContext)

        // ImagePickerHelper doit être créé avant setContent (enregistre les launchers)
        val imagePicker = ImagePickerHelper(this)

        val storage = AndroidStorage(applicationContext)
        val repository = LinkRepository(storage)
        val (sharedUrl, sharedTitle) = extractShared(intent)
        val sharedFolderToken = extractShareToken(intent)
        val appContext = applicationContext

        setContent {
            val viewModel = remember {
                LinkViewModel(repository).also {
                    it.authToken = storage.loadToken()
                    it.ogImageFetcher = { url -> OgImageFetcher.fetch(url) }
                    it.reminderScheduler = { link ->
                        if (link.eventDate != null) {
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
            TribbaeTheme {
                AppModern(vm = viewModel, sharedUrl = sharedUrl, sharedTitle = sharedTitle, sharedFolderToken = sharedFolderToken)
            }
        }
    }

    /**
     * Écran de lancement aux couleurs du logo A. Sur Android 12+, on laisse l'icône animée
     * (soleil levant, tipi, fanion) se jouer jusqu'au bout avant de partir ; puis le tipi
     * s'avance vers l'utilisateur pendant que le fond orange s'efface sur l'application.
     */
    private fun installLaunchTransition() {
        val splash = installSplashScreen()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val start = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - start < ICON_ANIMATION_MS }
        }
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            icon.animate()
                .scaleX(1.5f).scaleY(1.5f).alpha(0f)
                .setDuration(EXIT_MS)
                .setInterpolator(AccelerateInterpolator())
                .start()
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(EXIT_MS / 4)
                .setDuration(EXIT_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }
    }

    /** Jeton d'un lien de partage de dossier ouvert dans l'app (https://…/share/<jeton>). */
    private fun extractShareToken(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val segments = intent.data?.pathSegments ?: return null
        return segments.takeIf { it.size == 2 && it[0] == "share" }?.get(1)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{16,64}")) }
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
        if (url != null) return url to null
        return null to (subject.ifBlank { text }.take(300).ifBlank { null })
    }

    private companion object {
        /** Durée de l'icône animée (splash_icon_animated.xml : le fanion finit de flotter à ~1 s). */
        const val ICON_ANIMATION_MS = 1000L
        const val EXIT_MS = 320L
    }
}
