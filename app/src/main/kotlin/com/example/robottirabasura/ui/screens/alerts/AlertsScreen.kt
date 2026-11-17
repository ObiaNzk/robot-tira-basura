package com.example.robottirabasura.ui.screens.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.robottirabasura.domain.model.Alert
import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.rules.Availability
import com.example.robottirabasura.ui.components.StatusChip
import com.example.robottirabasura.ui.components.Tone
import com.example.robottirabasura.ui.components.formatTime
import com.example.robottirabasura.ui.components.label
import com.example.robottirabasura.ui.components.tone
import com.example.robottirabasura.ui.preview.PreviewData
import com.example.robottirabasura.ui.theme.RobotTheme

@Composable
fun AlertsRoute(viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlertsContent(state, onAcknowledge = viewModel::onAcknowledge)
}

@Composable
fun AlertsContent(state: AlertsUiState, onAcknowledge: (String) -> Unit) {
    if (state.alerts.isEmpty()) {
        Text(stringResource(R.string.alerts_empty), Modifier.padding(16.dp))
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.alerts, key = { it.id }) { alert ->
            AlertRow(alert, state.ack, pending = alert.id in state.pendingAckIds, onAcknowledge = { onAcknowledge(alert.id) })
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, ack: Availability, pending: Boolean, onAcknowledge: () -> Unit) {
    val icon = when (alert.severity) {
        AlertSeverity.INFO -> Icons.Default.Info
        AlertSeverity.ADVERTENCIA -> Icons.Default.Warning
        AlertSeverity.CRITICA -> Icons.Default.Error
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = stringResource(alert.severity.label))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(alert.type.label), style = MaterialTheme.typography.titleSmall)
                    Text(formatTime(alert.createdAtMillis), style = MaterialTheme.typography.bodySmall)
                }
                StatusChip(stringResource(alert.severity.label), alert.severity.tone)
            }
            when {
                alert.acknowledged -> StatusChip(stringResource(R.string.alert_acknowledged), Tone.NEUTRAL)
                pending -> StatusChip(stringResource(R.string.status_pending), Tone.NEUTRAL)
                else -> FilledTonalButton(onClick = onAcknowledge, enabled = ack.enabled) { Text(stringResource(R.string.cmd_ack)) }
            }
            if (!alert.acknowledged && !ack.enabled && ack.reason != null) {
                Text(stringResource(ack.reason.label), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlertsPreview() {
    RobotTheme {
        AlertsContent(
            AlertsUiState(PreviewData.alerts, LinkState.CONECTADO, Availability.ENABLED),
            onAcknowledge = {},
        )
    }
}
