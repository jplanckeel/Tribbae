package data

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gère la sélection d'image depuis la galerie ou l'appareil photo.
 * Copie l'image dans le stockage interne de l'app pour la persistance.
 */
class ImagePickerHelper(private val activity: ComponentActivity) {

    private var onImagePicked: ((String) -> Unit)? = null
    private var cameraUri: Uri? = null

    // Launcher galerie
    val galleryLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { copyToInternal(it)?.let { path -> onImagePicked?.invoke(path) } }
        }

    // Launcher appareil photo
    val cameraLauncher: ActivityResultLauncher<Uri> =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraUri?.let { uri -> copyToInternal(uri)?.let { path -> onImagePicked?.invoke(path) } }
            }
        }

    fun pickFromGallery(onResult: (String) -> Unit) {
        onImagePicked = onResult
        galleryLauncher.launch("image/*")
    }

    fun pickFromCamera(onResult: (String) -> Unit) {
        onImagePicked = onResult
        val photoFile = File(activity.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", photoFile)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    /** Copie l'image dans le stockage interne et retourne le chemin file:// */
    private fun copyToInternal(uri: Uri): String? {
        return try {
            val dir = File(activity.filesDir, "images").also { it.mkdirs() }
            val dest = File(dir, "img_${System.currentTimeMillis()}.jpg")
            activity.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            "file://${dest.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }
}
