package com.example.robottirabasura.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.ui.components.ConfirmDialog
import com.example.robottirabasura.ui.components.StatusChip
import com.example.robottirabasura.ui.components.formatDateTime
import com.example.robottirabasura.ui.components.formatTime
import com.example.robottirabasura.ui.components.label
import com.example.robottirabasura.ui.components.tone
import com.example.robottirabasura.ui.preview.PreviewData
import com.example.robottirabasura.ui.theme.RobotTheme

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContentView(
        state, onTab = viewModel::onTab, onClear = viewModel::onClear,
        onConfirmClear = viewModel::onConfirmClear, onDismissClear = viewModel::onDismissClear,
    )
}

@Composable
fun HistoryContentView(
    state: HistoryUiState,
    onTab: (HistoryTab) -> Unit,
    onClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
            Tab(state.tab == HistoryTab.MISIONES, { onTab(HistoryTab.MISIONES) }, text = { Text(stringResource(R.string.history_missions)) })
            Tab(state.tab == HistoryTab.EVENTOS, { onTab(HistoryTab.EVENTOS) }, text = { Text(stringResource(R.string.history_events)) })
        }
        when (val content = state.content) {
            HistoryContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is HistoryContent.Loaded -> {
                val empty = if (state.tab == HistoryTab.MISIONES) content.missions.isEmpty() else content.events.isEmpty()
                if (empty) {
                    Text(stringResource(R.string.history_empty), Modifier.padding(16.dp))
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.tab == HistoryTab.MISIONES) {
                            items(content.missions, key = { it.id }) { MissionRow(it) }
                        } else {
                            items(content.events) { EventRow(it) }
                        }
                        item {
                            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.history_clear), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmingClear) {
        ConfirmDialog(
            stringResource(R.string.history_clear_title), stringResource(R.string.history_clear_body),
            onConfirm = onConfirmClear, onDismiss = onDismissClear, danger = true,
        )
    }
}

@Composable
private fun MissionRow(mission: MissionRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.history_mission_line, mission.bolsasId, mission.tachoId, mission.distanceMeters),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(formatDateTime(mission.startedAtMillis), style = MaterialTheme.typography.bodySmall)
            }
            StatusChip(stringResource(mission.result.label()), mission.result.tone())
        }
        HorizontalDivider()
    }
}

@Composable
private fun EventRow(event: RobotEvent) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(event.timestampMillis), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(1f)) {
            Text(event.type.name.replace('_', ' ').lowercase(), style = MaterialTheme.typography.labelMedium)
            Text(event.detail, style = MaterialTheme.typography.bodyMedium)
        }
        StatusChip(stringResource(event.severity.label), event.severity.tone)
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryPreview() {
    RobotTheme {
        HistoryContentView(
            HistoryUiState(content = HistoryContent.Loaded(PreviewData.missions, PreviewData.events)),
            onTab = {}, onClear = {}, onConfirmClear = {}, onDismissClear = {},
        )
    }
}
