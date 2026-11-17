package com.example.robottirabasura.ui.screens.select

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.robottirabasura.R
import com.example.robottirabasura.domain.repository.RobotInfo
import com.example.robottirabasura.ui.components.StatusChip
import com.example.robottirabasura.ui.components.Tone
import com.example.robottirabasura.ui.theme.RobotTheme

@Composable
fun RobotSelectRoute(onOpenSupervision: () -> Unit, viewModel: RobotSelectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is RobotSelectEffect.OpenSupervision) onOpenSupervision() }
    }
    RobotSelectContent(state, onConnect = viewModel::onConnect, onOpen = onOpenSupervision)
}

@Composable
fun RobotSelectContent(state: RobotSelectUiState, onConnect: (String) -> Unit, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.select_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.select_subtitle), style = MaterialTheme.typography.bodyLarge)
        state.robots.forEach { robot ->
            val connected = robot.id == state.connectedId
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(robot.alias, style = MaterialTheme.typography.titleMedium)
                            Text(robot.model, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (connected) StatusChip(stringResource(R.string.select_connected), Tone.OK)
                    }
                    if (robot.simulated) {
                        Text(
                            stringResource(R.string.select_simulated_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { if (connected) onOpen() else onConnect(robot.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (connected) R.string.select_open else R.string.select_connect))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RobotSelectPreview() {
    RobotTheme {
        RobotSelectContent(
            RobotSelectUiState(listOf(RobotInfo("g1", "Unitree G1 EDU (simulado)", "G1 · Patio", true))),
            onConnect = {}, onOpen = {},
        )
    }
}
