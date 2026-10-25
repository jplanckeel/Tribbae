package ui

import androidx.compose.ui.graphics.vector.ImageVector

/** Icône d'un dossier, dessinée comme sur le web (ui.campfire.folderGlyph). */
fun folderIconVector(folder: data.Folder): ImageVector = ui.campfire.folderGlyph(folder.icon)
