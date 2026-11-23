package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import data.Zoom
import ui.campfire.CircleIconButton

/**
 * Image d'une idée en plein écran : pincer ou double-toucher pour agrandir,
 * glisser pour se déplacer ; la croix, le retour ou un toucher sur l'image
 * entière ferment. Même écran sur le web (components/ImageViewer.tsx).
 */
@Composable
fun ImageViewer(url: String, title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        fun apply(s: Float, x: Float, y: Float) {
            val c = Zoom.clampScale(s)
            val (px, py) = Zoom.clampPan(x, y, c, size.width.toFloat(), size.height.toFloat())
            scale = c
            offset = Offset(px, py)
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                url, title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(vertical = 56.dp)
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (scale <= 1.01f) onDismiss() },
                            onDoubleTap = { at ->
                                val next = Zoom.toggleScale(scale)
                                // Agrandit là où l'on touche
                                val cx = at.x - size.width / 2f
                                val cy = at.y - size.height / 2f
                                if (next > 1f) apply(next, -cx * (next - 1), -cy * (next - 1)) else apply(1f, 0f, 0f)
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = scale * zoom
                            apply(next, offset.x + pan.x, offset.y + pan.y)
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
            )
            CircleIconButton(
                Icons.Outlined.Close, "Fermer l'image", onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp),
                background = Color.White.copy(alpha = 0.15f), tint = Color.White,
            )
            Text(
                if (scale > 1.01f) "Glissez pour vous déplacer · double-touchez pour revenir" else "Pincez ou double-touchez pour agrandir",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(16.dp),
            )
        }
    }
}
