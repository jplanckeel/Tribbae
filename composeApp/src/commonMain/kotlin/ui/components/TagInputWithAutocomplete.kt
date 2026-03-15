package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Champ de saisie de tags avec autocomplétion dynamique et suggestions.
 *
 * @param tagsList tags actuellement sélectionnés
 * @param onTagsChanged callback quand la liste change
 * @param allAvailableTags tous les tags connus (pour autocomplétion + suggestions)
 */
@Composable
fun TagInputWithAutocomplete(
    tagsList: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    allAvailableTags: List<String>,
    modifier: Modifier = Modifier
) {
    var tagInput by remember { mutableStateOf("") }

    // Suggestions d'autocomplétion basées sur la saisie
    val autocompleteSuggestions = remember(tagInput, tagsList, allAvailableTags) {
        if (tagInput.length >= 1) {
            allAvailableTags
                .filter { it.contains(tagInput.trim(), ignoreCase = true) && !tagsList.contains(it) }
                .take(6)
        } else emptyList()
    }

    // Tags populaires à proposer quand le champ est vide
    val popularSuggestions = remember(tagsList, allAvailableTags) {
        allAvailableTags.filter { !tagsList.contains(it) }.take(8)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && !tagsList.contains(trimmed)) {
            onTagsChanged(tagsList + trimmed)
        }
        tagInput = ""
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Tags",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF374151)
        )

        // Chips des tags sélectionnés
        if (tagsList.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tagsList.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFF7ED)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tag,
                                fontSize = 13.sp,
                                color = Color(0xFFF97316),
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Supprimer",
                                tint = Color(0xFFF97316),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onTagsChanged(tagsList.filter { it != tag }) }
                            )
                        }
                    }
                }
            }
        }

        // Champ de saisie
        Box {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Ajouter un tag…", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFFF97316),
                        unfocusedBorderColor = Color(0xFFF3F4F6)
                    ),
                    singleLine = true
                )
                Button(
                    onClick = { addTag(tagInput) },
                    enabled = tagInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Dropdown d'autocomplétion
            if (autocompleteSuggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Column {
                        autocompleteSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addTag(suggestion) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF97316)
                                )
                                Text(
                                    text = suggestion,
                                    fontSize = 13.sp,
                                    color = Color(0xFF374151)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tags populaires suggérés (quand champ vide)
        if (tagInput.isEmpty() && popularSuggestions.isNotEmpty()) {
            Text(
                text = "Tags populaires",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                popularSuggestions.forEach { suggestion ->
                    Surface(
                        onClick = { addTag(suggestion) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF3F4F6)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(text = suggestion, fontSize = 11.sp, color = Color(0xFF6B7280))
                        }
                    }
                }
            }
        }
    }
}

