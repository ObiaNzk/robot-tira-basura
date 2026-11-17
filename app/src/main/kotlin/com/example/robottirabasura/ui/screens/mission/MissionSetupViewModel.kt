package com.example.robottirabasura.ui.screens.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.QrCodes
import com.example.robottirabasura.domain.model.QrError
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrParseResult
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.domain.model.RejectReason
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepKind
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.planning.MissionPlanner
import com.example.robottirabasura.domain.planning.PlanResult
import com.example.robottirabasura.domain.repository.RobotRepository
import com.example.robottirabasura.domain.rules.Availability
import com.example.robottirabasura.domain.rules.CommandContext
import com.example.robottirabasura.domain.rules.CommandRules
import com.example.robottirabasura.domain.usecase.ObserveRobotStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CameraState { CERRADA, PIDIENDO_PERMISO, ESCANEANDO, SIN_PERMISO }

sealed interface ScanFeedback {
    data class Read(val target: QrTarget) : ScanFeedback
    data class Invalid(val error: QrError) : ScanFeedback
}

sealed interface PlanPreview {
    data object NeedsQr : PlanPreview
    data class Ready(val distanceMeters: Double, val etaSeconds: Int, val steps: List<StepKind>) : PlanPreview
    data class Unreachable(val leg: StepKind) : PlanPreview
}

sealed interface StartStatus {
    data object Idle : StartStatus
    data object Sending : StartStatus
    data class Rejected(val reason: RejectReason?) : StartStatus
    data object NoAnswer : StartStatus
}

data class MissionSetupUiState(
    val site: SiteMap,
    val bolsas: QrTarget? = null,
    val tacho: QrTarget? = null,
    val camera: CameraState = CameraState.CERRADA,
    val feedback: ScanFeedback? = null,
    val preview: PlanPreview = PlanPreview.NeedsQr,
    val route: List<Cell> = emptyList(),
    val telemetry: Telemetry? = null,
    val start: Availability = Availability(false),
    val startStatus: StartStatus = StartStatus.Idle,
    val confirmingStart: Boolean = false,
    val demoCodes: List<String> = QrCodes.DEMO_CODES,
)

sealed interface MissionSetupEffect {
    data object OpenSupervision : MissionSetupEffect
}

private data class Form(
    val bolsas: QrTarget? = null,
    val tacho: QrTarget? = null,
    val camera: CameraState = CameraState.CERRADA,
    val feedback: ScanFeedback? = null,
    val sentCommandId: String? = null,
    val confirming: Boolean = false,
)

@HiltViewModel
class MissionSetupViewModel @Inject constructor(
    private val robot: RobotRepository,
    observeStatus: ObserveRobotStatusUseCase,
) : ViewModel() {

    private val form = MutableStateFlow(Form())

    private val _effects = MutableSharedFlow<MissionSetupEffect>()
    val effects: SharedFlow<MissionSetupEffect> = _effects.asSharedFlow()

    val uiState: StateFlow<MissionSetupUiState> = combine(form, observeStatus(), robot.commands) { f, status, commands ->
        val telemetry = status.telemetry
        val planResult = if (f.bolsas != null && f.tacho != null && telemetry != null) {
            MissionPlanner.plan(robot.site, telemetry.position.cell, f.bolsas, f.tacho, telemetry.extraObstacles)
        } else null
        val preview = when (planResult) {
            null -> PlanPreview.NeedsQr
            is PlanResult.Ready -> PlanPreview.Ready(
                distanceMeters = planResult.plan.distanceMeters(robot.site),
                etaSeconds = etaSeconds(planResult),
                steps = planResult.plan.steps.map { it.kind },
            )
            is PlanResult.Unreachable -> PlanPreview.Unreachable(planResult.leg)
            PlanResult.WrongTargets -> PlanPreview.NeedsQr
        }
        val sent = commands.firstOrNull { it.id == f.sentCommandId }
        val startStatus = when (sent?.status) {
            null, CommandStatus.CONFIRMADA -> StartStatus.Idle
            CommandStatus.PENDIENTE -> StartStatus.Sending
            CommandStatus.RECHAZADA -> StartStatus.Rejected(sent.rejectReason)
            CommandStatus.SIN_RESPUESTA -> StartStatus.NoAnswer
        }
        val pending = commands.filter { it.status == CommandStatus.PENDIENTE }.map { it.command.type }.toSet()
        MissionSetupUiState(
            site = robot.site,
            bolsas = f.bolsas,
            tacho = f.tacho,
            camera = f.camera,
            feedback = f.feedback,
            preview = preview,
            route = (planResult as? PlanResult.Ready)?.plan?.steps?.flatMap { it.path }.orEmpty(),
            telemetry = telemetry,
            start = CommandRules.availability(
                CommandType.INICIAR_MISION, telemetry,
                CommandContext(status.link, robot.site.safePoint, planReady = planResult is PlanResult.Ready, pending = pending),
            ),
            startStatus = startStatus,
            confirmingStart = f.confirming,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionSetupUiState(robot.site))

    init {
        // When the robot confirms the start, the form is cleared and the operator goes to supervision.
        viewModelScope.launch {
            robot.commands.collect { commands ->
                val id = form.value.sentCommandId ?: return@collect
                if (commands.firstOrNull { it.id == id }?.status == CommandStatus.CONFIRMADA) {
                    form.value = Form()
                    _effects.emit(MissionSetupEffect.OpenSupervision)
                }
            }
        }
    }

    fun onOpenCamera() = form.update { it.copy(camera = CameraState.PIDIENDO_PERMISO) }

    fun onCloseCamera() = form.update { it.copy(camera = CameraState.CERRADA) }

    fun onCameraPermissionResult(granted: Boolean) =
        form.update { it.copy(camera = if (granted) CameraState.ESCANEANDO else CameraState.SIN_PERMISO) }

    /** Called for every frame with a code (also for "simular lectura"). The same code read twice changes nothing. */
    fun onCodeScanned(raw: String) {
        when (val result = QrCodes.parse(raw, robot.site)) {
            QrParseResult.NotOurs -> Unit
            is QrParseResult.Invalid -> form.update { it.copy(feedback = ScanFeedback.Invalid(result.reason)) }
            is QrParseResult.Valid -> form.update { f ->
                val t = result.target
                val updated = if (t.kind == QrKind.BOLSAS) f.copy(bolsas = t) else f.copy(tacho = t)
                val complete = updated.bolsas != null && updated.tacho != null
                updated.copy(
                    feedback = ScanFeedback.Read(t),
                    // Both codes read: close the camera, nothing else to scan.
                    camera = if (complete) CameraState.CERRADA else updated.camera,
                    sentCommandId = null,
                )
            }
        }
    }

    fun onClear() = form.update { Form(camera = it.camera) }

    fun onStart() {
        if (!uiState.value.start.enabled) return
        form.update { it.copy(confirming = true) }
    }

    fun onDismissStart() = form.update { it.copy(confirming = false) }

    fun onConfirmStart() {
        val f = form.value
        val bolsas = f.bolsas ?: return
        val tacho = f.tacho ?: return
        val id = robot.send(RobotCommand.StartMission(bolsas, tacho))
        form.update { it.copy(confirming = false, sentCommandId = id) }
    }

    private fun etaSeconds(plan: PlanResult.Ready): Int {
        val walking = plan.plan.distanceMeters(robot.site) / MissionPlanner.NOMINAL_SPEED_MPS
        val manipulation = plan.plan.steps.sumOf { it.durationMillis } / 1000.0
        return (walking + manipulation).toInt()
    }
}
