package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import viewmodel.LinkViewModel

/**
 * Section de sélection d'image manuelle (galerie ou caméra).
 * Affichée quand il n'y a pas d'image OG scrappée.
 *
 * @param imageUrl URL actuelle de l'image (peut être vide, file://, ou https://)
 * @param viewModel pour accéder aux pickers injectés
 * @param onImageSelected callback quand une image est sélectionnée
 */
@Composable
fun ImagePickerSection(
    imageUrl: String,
    viewModel: LinkViewModel,
    onImageSelected: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Image", fontWeight = FontWeight.SemiBold, color = TextSecondary)

        if (imageUrl.isNotBlank()) {
            // Aperçu de l'image sélectionnée
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                NetworkImage(
                    url = imageUrl,
                    contentDescription = "Image sélectionnée",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Bouton supprimer
                IconButton(
                    onClick = { onImageSelected("") },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Supprimer l'image",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Bouton changer
                TextButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Changer", color = Color.White, fontSize = 12.sp)
                }
            }
        } else {
            // Zone de sélection vide
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .border(1.dp, OrangeLight.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Orange,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Ajouter une photo", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Galerie ou appareil photo", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }

    // Menu de choix galerie / caméra
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Choisir une image", fontWeight = FontWeight.Bold) },
            icon = { Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Orange) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Galerie
                    Card(
                        onClick = {
                            showMenu = false
                            viewModel.imagePickerGallery?.invoke { path -> onImageSelected(path) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Orange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("Galerie", fontWeight = FontWeight.SemiBold)
                                Text("Choisir depuis vos photos", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                    // Caméra
                    Card(
                        onClick = {
                            showMenu = false
                            viewModel.imagePickerCamera?.invoke { path -> onImageSelected(path) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BlueSky),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("Appareil photo", fontWeight = FontWeight.SemiBold)
                                Text("Prendre une nouvelle photo", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenu = false }) { Text("Annuler") }
            }
        )
    }
}
