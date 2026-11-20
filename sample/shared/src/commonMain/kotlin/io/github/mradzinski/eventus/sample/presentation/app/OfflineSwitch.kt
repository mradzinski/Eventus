package io.github.mradzinski.eventus.sample.presentation.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mradzinski.eventus.sample.resources.Res
import io.github.mradzinski.eventus.sample.resources.app_offline
import org.jetbrains.compose.resources.stringResource

/**
 * The switch that makes the sample behave as if the device had lost its connection. It is shown in the
 * app bar of every screen, so a failure can be tried at any moment, such as in the middle of an edit.
 */
@Composable
internal fun OfflineSwitch(
    state: AppUiState,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // The whole row is the control: the label can be tapped as well as the switch.
        modifier = modifier
            .toggleable(
                value = state.offline,
                role = Role.Switch,
                onValueChange = { onEvent(AppEvent.OnOfflineChange(it)) },
            ).padding(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(Res.string.app_offline),
            style = MaterialTheme.typography.labelLarge,
        )

        Switch(
            checked = state.offline,
            onCheckedChange = null,
        )
    }
}

@Preview
@Composable
private fun OfflineSwitchPreview() {
    MaterialTheme {
        OfflineSwitch(
            state = AppUiState(offline = true),
            onEvent = {},
        )
    }
}
