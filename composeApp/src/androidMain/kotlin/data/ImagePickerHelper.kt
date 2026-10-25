package data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gère la sélection d'image depuis la galerie ou l'appareil photo.
 * L'image est copiée dans le stockage interne de l'app, redressée et réduite
 * (plus grand côté 1600 px, JPEG sans métadonnées, comme le web et le
 * serveur) ; LinkViewModel l'envoie ensuite au serveur à l'enregistrement de
 * l'idée.
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

    /** Copie l'image (réduite et redressée) dans le stockage interne et retourne le chemin file:// */
    private fun copyToInternal(uri: Uri): String? {
        return try {
            val dir = File(activity.filesDir, "images").also { it.mkdirs() }
            val dest = File(dir, "img_${System.currentTimeMillis()}.jpg")
            val bitmap = decodeUpright(uri)
            if (bitmap != null) {
                dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bitmap.recycle()
            } else {
                // Format que l'appareil ne sait pas décoder : copié tel quel, le serveur tranchera
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            "file://${dest.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }

    /** Décode l'image à peine plus grande que MAX_SIDE, puis la redresse (EXIF) et la réduit. */
    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = activity.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
            }
        }
        if (matrix.isIdentity) return decoded
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (out !== decoded) decoded.recycle()
        return out
    }

    private companion object {
        /** Plus grand côté gardé, comme le serveur (backend/internal/photo) et le web. */
        const val MAX_SIDE = 1600
    }
}
