package com.linkkeeper.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import data.AndroidStorage
import data.ImagePickerHelper
import data.LinkRepository
import data.NotificationHelper
import data.OgImageFetcher
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
        val sharedUrl = extractSharedUrl(intent)
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
            MaterialTheme {
                App(vm = viewModel, sharedUrl = sharedUrl)
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        // Extraire l'URL du texte partagé (peut contenir du texte autour)
        val urlRegex = Regex("https?://\\S+")
        return urlRegex.find(text)?.value ?: text
    }
}
