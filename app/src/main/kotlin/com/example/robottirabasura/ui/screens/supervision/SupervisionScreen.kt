package com.example.robottirabasura.ui.screens.supervision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.robottirabasura.R
import com.example.robottirabasura.domain.model.CommandRecord
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.MissionProgress
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.StepStatus
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.model.WaitReason
import com.example.robottirabasura.domain.rules.Availability
import com.example.robottirabasura.ui.components.CommandButton
import com.example.robottirabasura.ui.components.CommandStyle
import com.example.robottirabasura.ui.components.ConfirmDialog
import com.example.robottirabasura.ui.components.SiteMapCanvas
import com.example.robottirabasura.ui.components.StaleBanner
import com.example.robottirabasura.ui.components.StatusChip
import com.example.robottirabasura.ui.components.Tone
import com.example.robottirabasura.ui.components.formatTime
import com.example.robottirabasura.ui.components.label
import com.example.robottirabasura.ui.components.tone
import com.example.robottirabasura.ui.preview.PreviewData
import com.example.robottirabasura.ui.theme.RobotTheme

@Composable
fun SupervisionRoute(viewModel: SupervisionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SupervisionContent(
        state = state,
        onCommand = viewModel::onCommand,
        onConfirm = viewModel::onConfirm,
        onDismissConfirmation = viewModel::onDismissConfirmation,
    )
}

@Composable
fun SupervisionContent(
    state: SupervisionUiState,
    onCommand: (CommandType) -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirmation: () -> Unit,
) {
    val telemetry = state.telemetry
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.link != LinkState.CONECTADO && state.ageSeconds != null) {
            item { StaleBanner(state.ageSeconds) }
        }
        if (telemetry == null) {
            item { Text(stringResource(R.string.sup_no_robot), Modifier.padding(16.dp)) }
            return@LazyColumn
        }
        item { MissionCard(telemetry, Modifier.padding(horizontal = 16.dp)) }
        if (telemetry.state == OperationalState.ESPERANDO_OPERADOR && telemetry.waitReason != null) {
            item { DecisionCard(telemetry.waitReason, state.actions, onCommand, Modifier.padding(horizontal = 16.dp)) }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sup_map), style = MaterialTheme.typography.titleMedium)
                    SiteMapCanvas(
                        site = state.site,
                        description = stringResource(R.string.map_description, telemetry.position.xMeters, telemetry.position.yMeters),
                        robot = telemetry.position,
                        headingDegrees = telemetry.headingDegrees,
                        route = telemetry.activeRoute,
                        bags = telemetry.mission?.plan?.bolsas?.cell,
                        bin = telemetry.mission?.plan?.tacho?.cell,
                        obstacles = telemetry.extraObstacles,
                    )
                }
            }
        }
        item { TelemetryCard(telemetry, Modifier.padding(horizontal = 16.dp)) }
        item { CommandsCard(state.actions, onCommand, Modifier.padding(horizontal = 16.dp)) }
        item { RecentCommandsCard(state.recentCommands, Modifier.padding(horizontal = 16.dp)) }
    }

    state.confirming?.let { type ->
        val (title, body) = when (type) {
            CommandType.CANCELAR -> R.string.confirm_title_cancel to R.string.confirm_body_cancel
            CommandType.DETENCION_OPERATIVA -> R.string.confirm_title_stop to R.string.confirm_body_stop
            CommandType.REGRESAR_A_PUNTO_SEGURO -> R.string.confirm_title_return to R.string.confirm_body_return
            else -> R.string.confirm_title_leave to R.string.confirm_body_leave
        }
        ConfirmDialog(
            title = stringResource(title), body = stringResource(body), onConfirm = onConfirm, onDismiss = onDismissConfirmation,
            danger = type == CommandType.CANCELAR || type == CommandType.DETENCION_OPERATIVA,
        )
    }
}

@Composable
private fun MissionCard(telemetry: Telemetry, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(stringResource(telemetry.state.label), telemetry.state.tone)
                Text(
                    formatTime(telemetry.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val mission = telemetry.mission
            if (mission == null) {
                Text(stringResource(R.string.sup_mission_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                MissionProgressBlock(mission)
            }
        }
    }
}

@Composable
private fun MissionProgressBlock(mission: MissionProgress) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.sup_mission_title, mission.plan.bolsas.id, mission.plan.tacho.id),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        StatusChip(stringResource(mission.result.label()), mission.result.tone())
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = { mission.progressPercent / 100f }, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.sup_progress, mission.progressPercent), style = MaterialTheme.typography.labelLarge)
    }
    mission.plan.steps.forEachIndexed { index, step ->
        val status = mission.stepStatuses[index]
        val icon = when (status) {
            StepStatus.COMPLETADO -> Icons.Default.CheckCircle
            StepStatus.EN_CURSO -> Icons.Default.Sync
            StepStatus.OMITIDO -> Icons.Default.RemoveCircleOutline
            StepStatus.PENDIENTE -> Icons.Default.RadioButtonUnchecked
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                icon, contentDescription = status.name.lowercase(), modifier = Modifier.size(18.dp),
                tint = if (status == StepStatus.EN_CURSO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(step.kind.label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (status == StepStatus.EN_CURSO) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DecisionCard(
    reason: WaitReason,
    actions: Map<CommandType, Availability>,
    onCommand: (CommandType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (reason == WaitReason.OBSTACULO) R.string.sup_decision_obstacle else R.string.sup_decision_bin_full),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val disabled = Availability(false)
            if (reason == WaitReason.OBSTACULO) {
                CommandButton(stringResource(R.string.cmd_replan), actions[CommandType.REPLANIFICAR] ?: disabled,
                    { onCommand(CommandType.REPLANIFICAR) }, style = CommandStyle.PRIMARY)
            } else {
                CommandButton(stringResource(R.string.cmd_retry_bin), actions[CommandType.REANUDAR] ?: disabled,
                    { onCommand(CommandType.REANUDAR) }, style = CommandStyle.PRIMARY)
                CommandButton(stringResource(R.string.cmd_leave), actions[CommandType.DEJAR_JUNTO_AL_TACHO] ?: disabled,
                    { onCommand(CommandType.DEJAR_JUNTO_AL_TACHO) })
            }
            CommandButton(stringResource(R.string.cmd_return), actions[CommandType.REGRESAR_A_PUNTO_SEGURO] ?: disabled,
                { onCommand(CommandType.REGRESAR_A_PUNTO_SEGURO) }, style = CommandStyle.OUTLINED)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelemetryCard(t: Telemetry, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sup_telemetry), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(stringResource(R.string.sup_battery), Modifier.weight(1f))
                    Text("${t.batteryPercent.toInt()} %", fontWeight = FontWeight.Bold)
                }
                val batteryTone = when {
                    t.batteryPercent <= 8 -> MaterialTheme.colorScheme.error
                    t.batteryPercent < 20 -> androidx.compose.ui.graphics.Color(0xFFE0A100)
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(progress = { (t.batteryPercent / 100).toFloat() }, color = batteryTone, modifier = Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(stringResource(R.string.sup_signal), "${t.signalPercent} %")
                Metric(stringResource(R.string.sup_position), stringResource(R.string.sup_position_value, t.position.xMeters, t.position.yMeters))
                Metric(stringResource(R.string.sup_movement), stringResource(R.string.sup_movement_value, t.speedMps, t.headingDegrees))
                Metric(stringResource(R.string.sup_alerts), t.unacknowledgedAlerts.size.toString())
            }
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(stringResource(R.string.sup_bags), t.bagsCollected.toString())
                Metric(stringResource(R.string.sup_payload), stringResource(R.string.sup_payload_value, t.payloadKg))
                Metric(stringResource(R.string.sup_bin), "${t.binFillPercent} %")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(Modifier.width(140.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CommandsCard(actions: Map<CommandType, Availability>, onCommand: (CommandType) -> Unit, modifier: Modifier = Modifier) {
    val disabled = Availability(false)
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sup_commands), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton(stringResource(R.string.cmd_pause), actions[CommandType.PAUSAR] ?: disabled,
                    { onCommand(CommandType.PAUSAR) }, Modifier.weight(1f), icon = Icons.Default.Pause)
                CommandButton(stringResource(R.string.cmd_resume), actions[CommandType.REANUDAR] ?: disabled,
                    { onCommand(CommandType.REANUDAR) }, Modifier.weight(1f), icon = Icons.Default.PlayArrow)
            }
            CommandButton(stringResource(R.string.cmd_return), actions[CommandType.REGRESAR_A_PUNTO_SEGURO] ?: disabled,
                { onCommand(CommandType.REGRESAR_A_PUNTO_SEGURO) }, icon = Icons.Default.Home)
            CommandButton(stringResource(R.string.cmd_cancel), actions[CommandType.CANCELAR] ?: disabled,
                { onCommand(CommandType.CANCELAR) }, style = CommandStyle.OUTLINED)
            CommandButton(stringResource(R.string.cmd_stop), actions[CommandType.DETENCION_OPERATIVA] ?: disabled,
                { onCommand(CommandType.DETENCION_OPERATIVA) }, style = CommandStyle.DANGER, icon = Icons.Default.Stop)
            Text(
                stringResource(R.string.sup_stop_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentCommandsCard(commands: List<CommandRecord>, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sup_recent_commands), style = MaterialTheme.typography.titleMedium)
            if (commands.isEmpty()) Text(stringResource(R.string.sup_no_commands), style = MaterialTheme.typography.bodyMedium)
            commands.forEach { CommandRow(it) }
        }
    }
}

@Composable
fun CommandRow(record: CommandRecord) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(record.command.type.label), style = MaterialTheme.typography.bodyMedium)
            val reason = record.rejectReason
            Text(
                formatTime(record.sentAtMillis) + (if (reason != null) " · " + stringResource(reason.label) else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(stringResource(record.status.label), record.status.tone)
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun SupervisionPreview() {
    RobotTheme {
        SupervisionContent(
            SupervisionUiState(
                site = PreviewData.site,
                telemetry = PreviewData.telemetryOnMission,
                link = LinkState.CONECTADO,
                ageSeconds = 0,
                recentCommands = listOf(PreviewData.command(CommandStatus.CONFIRMADA)),
                actions = SUPERVISION_COMMANDS.associateWith { Availability.ENABLED },
            ),
            onCommand = {}, onConfirm = {}, onDismissConfirmation = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun SupervisionStalePreview() {
    RobotTheme {
        SupervisionContent(
            SupervisionUiState(
                site = PreviewData.site,
                telemetry = PreviewData.telemetryWaiting,
                link = LinkState.DATOS_ANTIGUOS,
                ageSeconds = 7,
                recentCommands = listOf(PreviewData.command(CommandStatus.SIN_RESPUESTA)),
            ),
            onCommand = {}, onConfirm = {}, onDismissConfirmation = {},
        )
    }
}
