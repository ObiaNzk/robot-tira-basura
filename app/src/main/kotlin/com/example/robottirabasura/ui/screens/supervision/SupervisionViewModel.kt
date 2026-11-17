package com.example.robottirabasura.ui.screens.supervision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robottirabasura.domain.model.CommandRecord
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.repository.RobotRepository
import com.example.robottirabasura.domain.rules.Availability
import com.example.robottirabasura.domain.rules.CommandContext
import com.example.robottirabasura.domain.rules.CommandRules
import com.example.robottirabasura.domain.usecase.ObserveRobotStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Commands offered on this screen (start lives in the mission screen, alerts in the alerts screen). */
val SUPERVISION_COMMANDS = listOf(
    CommandType.PAUSAR, CommandType.REANUDAR, CommandType.CANCELAR, CommandType.REGRESAR_A_PUNTO_SEGURO,
    CommandType.DETENCION_OPERATIVA, CommandType.REPLANIFICAR, CommandType.DEJAR_JUNTO_AL_TACHO,
)

/** Commands that can't be undone with one tap ask for confirmation (accidental orders are a listed risk). */
val NEEDS_CONFIRMATION = setOf(
    CommandType.CANCELAR, CommandType.DETENCION_OPERATIVA, CommandType.REGRESAR_A_PUNTO_SEGURO,
    CommandType.DEJAR_JUNTO_AL_TACHO,
)

data class SupervisionUiState(
    val site: SiteMap,
    val telemetry: Telemetry? = null,
    val link: LinkState = LinkState.DESCONECTADO,
    val ageSeconds: Long? = null,
    val actions: Map<CommandType, Availability> = emptyMap(),
    val recentCommands: List<CommandRecord> = emptyList(),
    val confirming: CommandType? = null,
)

@HiltViewModel
class SupervisionViewModel @Inject constructor(
    private val robot: RobotRepository,
    observeStatus: ObserveRobotStatusUseCase,
) : ViewModel() {

    private val confirming = MutableStateFlow<CommandType?>(null)

    val uiState: StateFlow<SupervisionUiState> = combine(observeStatus(), robot.commands, confirming) { status, commands, confirm ->
        val pending = commands.filter { it.status == CommandStatus.PENDIENTE }.map { it.command.type }.toSet()
        val context = CommandContext(status.link, robot.site.safePoint, pending = pending)
        SupervisionUiState(
            site = robot.site,
            telemetry = status.telemetry,
            link = status.link,
            ageSeconds = status.ageMillis?.div(1000),
            actions = SUPERVISION_COMMANDS.associateWith { CommandRules.availability(it, status.telemetry, context) },
            recentCommands = commands.take(5),
            confirming = confirm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupervisionUiState(robot.site))

    fun onCommand(type: CommandType) {
        if (uiState.value.actions[type]?.enabled != true) return
        if (type in NEEDS_CONFIRMATION) confirming.value = type else send(type)
    }

    fun onConfirm() {
        val type = confirming.value ?: return
        confirming.update { null }
        send(type)
    }

    fun onDismissConfirmation() {
        confirming.value = null
    }

    private fun send(type: CommandType) {
        val command = when (type) {
            CommandType.PAUSAR -> RobotCommand.Pause
            CommandType.REANUDAR -> RobotCommand.Resume
            CommandType.CANCELAR -> RobotCommand.Cancel
            CommandType.REGRESAR_A_PUNTO_SEGURO -> RobotCommand.ReturnToSafePoint
            CommandType.DETENCION_OPERATIVA -> RobotCommand.OperationalStop
            CommandType.REPLANIFICAR -> RobotCommand.Replan
            CommandType.DEJAR_JUNTO_AL_TACHO -> RobotCommand.LeaveBesideBin
            CommandType.INICIAR_MISION, CommandType.RECONOCER_ALERTA -> return
        }
        robot.send(command)
    }
}
