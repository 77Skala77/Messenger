package app.securemessenger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

internal const val SEARCH_BUTTON_TAG = "chat_search_button"
internal const val SEARCH_FIELD_TAG = "chat_search_field"
internal const val SEARCH_CLEAR_TAG = "chat_search_clear"
internal const val SEARCH_EMPTY_TAG = "chat_search_empty"

/**
 * The top bar while the chat list is being searched (Telegram's search action bar): the field takes
 * the place of the title, the arrow closes the search, a cross clears the text. The search only
 * looks at what this phone already shows, the names and the newest texts; nothing is sent anywhere.
 */
@Composable
internal fun ChatSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.messenger
    TelegramTopBar(
        onBack = onClose,
        titleContent = {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MessengerType.TopBarTitle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(colors.fab),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focus).testTag(SEARCH_FIELD_TAG),
                decorationBox = { field ->
                    Box(Modifier.padding(start = 18.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.search_hint), style = MessengerType.TopBarTitle, color = colors.hint, maxLines = 1)
                        }
                        field()
                    }
                },
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                TopBarAction(
                    onClick = { onQueryChange("") },
                    description = stringResource(R.string.search_clear),
                    modifier = Modifier.testTag(SEARCH_CLEAR_TAG),
                ) { TelegramIcon(R.drawable.tg_msg_close) }
            }
        },
    )
}
