package one.mindly.pip.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import one.mindly.pip.ui.theme.PipSpacing

/**
 * A yes/no dialog that keeps clear of the screen edges. The platform's default dialog width
 * (usePlatformDefaultWidth) comes from the phone's own theme, which on Samsung is edge to edge;
 * turning it off lets the Material dialog size itself, with [PipSpacing.l] on each side.
 */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = PipSpacing.l),
    )
}
