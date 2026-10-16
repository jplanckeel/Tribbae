package com.linkkeeper.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createChannel(applicationContext)

        // ImagePickerHelper doit être créé avant setContent (enregistre les launchers)
        val imagePicker = ImagePickerHelper(this)

        val storage = AndroidStorage(applicationContext)
        val repository = LinkRepository(storage)
        val (sharedUrl, sharedTitle) = extractShared(intent)
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
                AppModern(vm = viewModel, sharedUrl = sharedUrl, sharedTitle = sharedTitle)
            }
        }
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
}
