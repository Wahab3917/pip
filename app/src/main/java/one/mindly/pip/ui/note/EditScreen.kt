package one.mindly.pip.ui.note

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import one.mindly.pip.R
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipType

/**
 * Editing a thought's text, full screen: just the words, large, in the serif, with the cursor
 * at the end. ✕ or back discards the changes; Save keeps them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(initialText: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.note_cancel))
                    }
                },
                actions = { TextButton(onClick = { onSave(value.text) }) { Text(stringResource(R.string.note_save)) } },
            )
        },
    ) { padding ->
        val label = stringResource(R.string.note_transcript_label)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = PipType.thoughtLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = PipSpacing.l, vertical = PipSpacing.m)
                .focusRequester(focus)
                .semantics { contentDescription = label },
        )
    }
}
