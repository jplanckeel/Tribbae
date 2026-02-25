package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.net.URLEncoder

/**
 * Champ de saisie de lieu avec autocomplétion via Nominatim (OpenStreetMap).
 * Aucune clé API requise.
 */
@Composable
fun LocationAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showDropdown by remember { mutableStateOf(false) }

    // Déclenche la recherche avec debounce de 400ms
    LaunchedEffect(value) {
        searchJob?.cancel()
        if (value.length < 3) {
            suggestions = emptyList()
            showDropdown = false
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(400)
            val results = fetchNominatimSuggestions(value)
            suggestions = results
            showDropdown = results.isNotEmpty()
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                if (it.isBlank()) showDropdown = false
            },
            label = { Text("Lieu") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Orange
                )
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                focusedContainerColor = CardColor,
                unfocusedContainerColor = CardColor
            )
        )

        if (showDropdown && suggestions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    suggestions.take(5).forEach { suggestion ->
                        TextButton(
                            onClick = {
                                onValueChange(suggestion)
                                showDropdown = false
                                suggestions = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Orange
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = suggestion,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Appel réseau Nominatim sur le thread IO.
 * Retourne une liste de noms de lieux formatés.
 */
private suspend fun fetchNominatimSuggestions(query: String): List<String> =
    withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search" +
                "?q=$encoded&format=json&limit=5&addressdetails=1"
            val connection = URL(url).openConnection()
            connection.setRequestProperty("User-Agent", "Tribbae-App/1.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val response = connection.getInputStream().bufferedReader().readText()
            val json = Json { ignoreUnknownKeys = true }
            val array = json.parseToJsonElement(response).jsonArray
            array.map { element ->
                element.jsonObject["display_name"]?.jsonPrimitive?.content ?: ""
            }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
