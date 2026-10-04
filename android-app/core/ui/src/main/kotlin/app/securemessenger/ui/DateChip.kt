package app.securemessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The date patterns of the language in use; they change with the language, which recreates the screen. */
@Composable
fun rememberDatePatterns(): DatePatterns = DatePatterns(
    listOld = stringResource(R.string.date_pattern_list_old),
    listMonth = stringResource(R.string.date_pattern_list_month),
    chipRecent = stringResource(R.string.date_pattern_chip_recent),
    chipOld = stringResource(R.string.date_pattern_chip_old),
)

/**
 * The pill with the date between two days of a conversation: centered, 14 sp, 7 dp above it,
 * semi-transparent so the wallpaper shows through (`docs/PHASE6_PORTING_NOTES.md`, 2.8).
 */
@Composable
fun DateChip(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    Box(modifier.fillMaxWidth().padding(top = 7.dp, bottom = 7.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MessengerType.DateChip,
            color = colors.chipText,
            modifier = Modifier
                .background(colors.chip, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        )
    }
}
