package com.example.robottirabasura.ui.screens.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.robottirabasura.R
import com.example.robottirabasura.ui.theme.RobotTheme
import kotlin.math.roundToInt

@Composable
fun SimulatorRoute(onMessage: (String) -> Unit, viewModel: SimulatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done = stringResource(R.string.sim_done)
    val failed = stringResource(R.string.sim_obstacle_failed)
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            onMessage(if (it == SimulatorEffect.ObstacleFailed) failed else done)
        }
    }
    SimulatorContent(
        state = state,
        onLinkUp = viewModel::onLinkUp,
        onDropNextResponse = viewModel::onDropNextResponse,
        onRejectNext = viewModel::onRejectNext,
        onInjectObstacle = viewModel::onInjectObstacle,
        onClearObstacles = viewModel::onClearObstacles,
        onBinFull = viewModel::onBinFull,
        onPersonNearby = viewModel::onPersonNearby,
        onBatteryTarget = viewModel::onBatteryTarget,
        onApplyBattery = viewModel::onApplyBattery,
    )
}

@Composable
fun SimulatorContent(
    state: SimulatorUiState,
    onLinkUp: (Boolean) -> Unit,
    onDropNextResponse: () -> Unit,
    onRejectNext: () -> Unit,
    onInjectObstacle: () -> Unit,
    onClearObstacles: () -> Unit,
    onBinFull: (Boolean) -> Unit,
    onPersonNearby: (Boolean) -> Unit,
    onBatteryTarget: (Int) -> Unit,
    onApplyBattery: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.sim_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.sim_note), style = MaterialTheme.typography.bodyMedium)
        if (!state.connected) {
            Text(stringResource(R.string.sim_not_connected), color = MaterialTheme.colorScheme.error)
            return@Column
        }
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(stringResource(R.string.sim_link), state.linkUp, onLinkUp)
                FilledTonalButton(onDropNextResponse, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sim_drop)) }
                FilledTonalButton(onRejectNext, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sim_reject)) }
            }
        }
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onInjectObstacle, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sim_obstacle)) }
                OutlinedButton(onClearObstacles, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sim_clear_obstacles)) }
                SwitchRow(stringResource(R.string.sim_bin_full), state.binFull, onBinFull)
                SwitchRow(stringResource(R.string.sim_person), state.personNearby, onPersonNearby)
            }
        }
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sim_battery), Modifier.weight(1f))
                    Text(stringResource(R.string.sim_battery_value, state.batteryTarget))
                }
                Slider(
                    value = state.batteryTarget.toFloat(),
                    onValueChange = { onBatteryTarget(it.roundToInt()) },
                    valueRange = 0f..100f,
                )
                FilledTonalButton(onApplyBattery, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sim_battery)) }
                state.currentBattery?.let {
                    Text(stringResource(R.string.sup_battery) + ": " + stringResource(R.string.sim_battery_value, it))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun SimulatorPreview() {
    RobotTheme {
        SimulatorContent(
            SimulatorUiState(connected = true, currentBattery = 64),
            {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
