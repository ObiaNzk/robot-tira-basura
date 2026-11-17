package com.example.robottirabasura.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robottirabasura.domain.model.Alert
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.repository.RobotRepository
import com.example.robottirabasura.domain.rules.Availability
import com.example.robottirabasura.domain.rules.CommandContext
import com.example.robottirabasura.domain.rules.CommandRules
import com.example.robottirabasura.domain.usecase.ObserveRobotStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val link: LinkState = LinkState.DESCONECTADO,
    val ack: Availability = Availability(false),
    /** Alerts whose acknowledgement was sent and is waiting for the robot's answer. */
    val pendingAckIds: Set<String> = emptySet(),
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val robot: RobotRepository,
    observeStatus: ObserveRobotStatusUseCase,
) : ViewModel() {

    val uiState: StateFlow<AlertsUiState> = combine(observeStatus(), robot.commands) { status, commands ->
        val pendingAcks = commands
            .filter { it.status == CommandStatus.PENDIENTE }
            .mapNotNull { (it.command as? RobotCommand.AcknowledgeAlert)?.alertId }
            .toSet()
        AlertsUiState(
            // Open alerts first, the most severe on top, then the newest.
            alerts = status.telemetry?.alerts.orEmpty().sortedWith(
                compareBy<Alert> { it.acknowledged }
                    .thenByDescending { it.severity.ordinal }
                    .thenByDescending { it.createdAtMillis }
            ),
            link = status.link,
            ack = CommandRules.availability(
                CommandType.RECONOCER_ALERTA, status.telemetry, CommandContext(status.link, robot.site.safePoint),
            ),
            pendingAckIds = pendingAcks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    fun onAcknowledge(alertId: String) {
        val state = uiState.value
        if (!state.ack.enabled || alertId in state.pendingAckIds) return
        if (state.alerts.none { it.id == alertId && !it.acknowledged }) return
        robot.send(RobotCommand.AcknowledgeAlert(alertId))
    }

}
