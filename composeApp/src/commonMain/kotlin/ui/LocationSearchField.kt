package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf(value) }
    
    // Synchroniser avec la valeur externe
    LaunchedEffect(value) {
        if (value != searchQuery) {
            searchQuery = value
        }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = { query ->
            searchQuery = query
            onValueChange(query)
        },
        label = { Text("Lieu", fontSize = 14.sp) },
        placeholder = { Text("Ex: Paris, Lyon, Marseille…", fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = {
                    searchQuery = ""
                    onValueChange("")
                }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Effacer",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF9FAFB),
            unfocusedContainerColor = Color(0xFFF9FAFB),
            focusedBorderColor = Color(0xFFF97316),
            unfocusedBorderColor = Color(0xFFE5E7EB)
        ),
        supportingText = {
            Text(
                "Entrez une ville ou une adresse",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF)
            )
        }
    )
}

