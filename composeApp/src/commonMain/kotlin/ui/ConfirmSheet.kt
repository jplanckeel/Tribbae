package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.campfire.ButtonKind
import ui.campfire.CampButton
import ui.campfire.SheetHandle
import ui.campfire.display

/** Confirmation courte du design system (composant Sheet) : question, conséquence, deux boutons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = display(24.sp))
            Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = Ecorce2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampButton("Annuler", onDismiss, Modifier.weight(1f), kind = ButtonKind.Ghost)
                CampButton(confirmLabel, onConfirm, Modifier.weight(1f), kind = ButtonKind.Dark)
            }
        }
    }
}
