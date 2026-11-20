package io.github.mradzinski.eventus.sample.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mradzinski.eventus.sample.resources.Res
import io.github.mradzinski.eventus.sample.resources.app_bar_back
import org.jetbrains.compose.resources.stringResource

private val BarHeight = 64.dp

/**
 * The bar at the top of every screen. Each screen puts it in its own scaffold and says what it needs:
 * its title, whether there is a way back, and its own buttons. That keeps a button with the screen
 * whose event it sends, so a screen without a button simply does not pass one.
 *
 * @param onBackClick Shows a back button when given. A screen with nowhere to go back to leaves it out.
 * @param actions The screen's own buttons, placed after the title.
 */
@Composable
internal fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = BarHeight)
                .padding(start = 4.dp, end = 12.dp),
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.app_bar_back),
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )

            actions()
        }
    }
}

@Preview
@Composable
private fun AppTopBarWithBackAndActionsPreview() {
    MaterialTheme {
        AppTopBar(
            title = "Edit task",
            onBackClick = {},
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppTopBarTitleOnlyPreview() {
    MaterialTheme {
        AppTopBar(title = "Tasks")
    }
}
