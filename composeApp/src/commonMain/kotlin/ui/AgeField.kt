package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AgeUnit(val label: String, val suffix: String) {
    MOIS("Mois", "mois"),
    ANS("Ans", "ans")
}

/**
 * Parse un ageRange existant ("3 ans", "18 mois", "6") en (valeur, unité).
 */
private fun parseAgeRange(ageRange: String): Pair<String, AgeUnit> {
    val lower = ageRange.lowercase().trim()
    val number = lower.replace(Regex("[^0-9]"), "")
    val unit = if (lower.contains("mois")) AgeUnit.MOIS else AgeUnit.ANS
    return number to unit
}

/**
 * Champ âge avec clavier numérique et toggle mois/années.
 * Stocke la valeur sous forme "X mois" ou "X ans".
 */
@Composable
fun AgeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (initialNumber, initialUnit) = remember(value) { parseAgeRange(value) }
    var number by remember(value) { mutableStateOf(initialNumber) }
    var unit by remember(value) { mutableStateOf(initialUnit) }

    fun emitValue(n: String, u: AgeUnit) {
        onValueChange(if (n.isBlank()) "" else "$n ${u.suffix}")
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = number,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }
                number = filtered
                emitValue(filtered, unit)
            },
            label = { Text("Âge") },
            leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                focusedContainerColor = CardColor,
                unfocusedContainerColor = CardColor
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgeUnit.entries.forEach { u ->
                FilterChip(
                    selected = unit == u,
                    onClick = {
                        unit = u
                        emitValue(number, u)
                    },
                    label = { Text(u.label, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange,
                        selectedLabelColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            }
        }
    }
}
