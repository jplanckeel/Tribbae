package ui.campfire

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.AgeRanges
import data.Child
import ui.*

private typealias Range = Pair<Float, Float>

private val ALL: Range = 0f to AgeRanges.OPEN
private val BOUNDS = setOf(0f, 3f, 6f, 10f, AgeRanges.OPEN)

/**
 * Tranche d'âge d'une idée, sans rien taper : tranches en un toucher (même
 * découpage que les filtres), raccourcis vers l'âge des enfants de la tribu,
 * et réglage précis au besoin (par 6 mois jusqu'à 2 ans). Même comportement
 * que AgePicker du web (frontend/src/components/AgePicker.tsx).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgePicker(value: String, onChange: (String) -> Unit, kids: List<Child>) {
    val range = AgeRanges.parse(value)
    val all = range == ALL
    val children = kids.filter { it.name.isNotBlank() }.map { it to AgeRanges.snap(ageInMonths(it.birthDate) / 12f) }
    fun set(r: Range?) = onChange(r?.let { AgeRanges.format(it) } ?: "")
    // Réglage précis ouvert d'office si la tranche ne tombe pas sur les tranches proposées
    var precise by remember {
        mutableStateOf(range != null && !all && !(range.first in BOUNDS && range.second in BOUNDS) && children.none { range == (it.second to it.second) })
    }

    // Toucher une tranche : l'ajouter (la tranche s'élargit), ou la retirer d'un bord
    fun toggleBand(b: Range) {
        when {
            range == null || all -> set(b)
            !AgeRanges.covers(range, b) -> set(AgeRanges.hull(range, b))
            range == b -> set(null)
            b.first == range.first -> set(b.second to range.second)
            b.second == range.second -> set(range.first to b.first)
            else -> set(b)
        }
    }
    fun toggleAge(a: Float) {
        val r = a to a
        when {
            range == null || all -> set(r)
            !AgeRanges.covers(range, r) -> set(AgeRanges.hull(range, r))
            else -> set(if (range == r) null else r)
        }
    }

    val shown: Range = if (range != null && !all) range else 3f to 6f
    fun step(end: Int, dir: Int) {
        val stops = if (end == 0) AgeRanges.STOPS.filter { it < AgeRanges.OPEN } else AgeRanges.STOPS.filter { it > 0f }
        val i = stops.indexOf(if (end == 0) shown.first else shown.second).coerceAtLeast(0)
        val next = stops[(i + dir).coerceIn(0, stops.lastIndex)]
        set(if (end == 0) next to maxOf(next, shown.second) else minOf(shown.first, next) to next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Âge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec, modifier = Modifier.weight(1f))
            if (value.isNotBlank()) {
                Text(range?.let { AgeRanges.format(it) } ?: value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
                Text(
                    "Effacer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Terracotta,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { set(null) }.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else {
                Text("Non précisé", fontSize = 13.sp, color = TextMuted)
            }
        }
        if (value.isNotBlank() && range == null) {
            Text("Saisie actuelle : « $value ». Choisissez une tranche pour la remplacer.", fontSize = 12.sp, color = TextSec)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Tous âges", all, { set(if (all) null else ALL) })
            AgeRanges.BANDS.forEach { (label, b) ->
                Pill(label, range != null && !all && AgeRanges.covers(range, b), { toggleBand(b) })
            }
        }

        if (children.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("Pour :", fontSize = 12.sp, color = TextSec)
                children.forEach { (kid, age) ->
                    val on = range != null && !all && AgeRanges.covers(range, age to age)
                    Box(
                        Modifier.height(34.dp).clip(CircleShape).background(if (on) Terracotta else Color.White)
                            .clickable(role = Role.Checkbox) { toggleAge(age) }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${kid.name} · ${AgeRanges.label(age)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Ecorce)
                    }
                }
            }
        }

        if (precise) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("De", fontSize = 14.sp, color = TextSec, modifier = Modifier.width(28.dp))
                    Stepper("Âge minimum", if (shown.first == 0f) "Naissance" else AgeRanges.label(shown.first), { step(0, -1) }, { step(0, 1) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("à", fontSize = 14.sp, color = TextSec, modifier = Modifier.width(28.dp))
                    Stepper("Âge maximum", if (shown.second >= AgeRanges.OPEN) "et +" else AgeRanges.label(shown.second), { step(1, -1) }, { step(1, 1) })
                }
            }
        } else {
            Text(
                "Plus précis (mois, âges exacts)…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Terracotta,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { precise = true }.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun Stepper(label: String, text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton(Icons.Outlined.Remove, "$label : moins", onMinus)
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 84.dp))
        StepButton(Icons.Outlined.Add, "$label : plus", onPlus)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Sable2).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Ecorce, modifier = Modifier.size(18.dp)) }
}
