package com.example.robottirabasura.ui.preview

import com.example.robottirabasura.domain.model.Alert
import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.AlertType
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.CommandRecord
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.EventType
import com.example.robottirabasura.domain.model.MissionProgress
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.MissionResult
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.Position
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepStatus
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.model.WaitReason
import com.example.robottirabasura.domain.planning.MissionPlanner
import com.example.robottirabasura.domain.planning.PlanResult
import com.example.robottirabasura.domain.rules.Availability
import com.example.robottirabasura.ui.screens.mission.MissionSetupUiState
import com.example.robottirabasura.ui.screens.mission.PlanPreview

/** Fabricated states for @Preview and UI tests. Fictional data only. */
object PreviewData {
    private const val NOW = 1_762_600_000_000L
    val site: SiteMap = SiteMap.DEMO
    val bolsas = QrTarget(QrKind.BOLSAS, "B1", Cell(1, 9))
    val tacho = QrTarget(QrKind.TACHO, "T1", Cell(16, 1))
    private val plan = (MissionPlanner.plan(site, site.safePoint, bolsas, tacho) as PlanResult.Ready).plan

    val alerts = listOf(
        Alert("a1", AlertType.OBSTACULO, AlertSeverity.ADVERTENCIA, NOW - 5_000),
        Alert("a2", AlertType.BATERIA_BAJA, AlertSeverity.ADVERTENCIA, NOW - 60_000, acknowledged = true),
    )

    val telemetryOnMission = Telemetry(
        robotId = "g1-sim-01",
        timestampMillis = NOW,
        signalPercent = 82,
        batteryPercent = 64.0,
        state = OperationalState.EN_MISION,
        mission = MissionProgress(
            missionId = "m1",
            plan = plan,
            stepStatuses = listOf(StepStatus.COMPLETADO, StepStatus.COMPLETADO, StepStatus.EN_CURSO) + List(4) { StepStatus.PENDIENTE },
            currentStep = 2,
            progressPercent = 38,
        ),
        position = Position(2.75, 3.25, Cell(5, 6)),
        headingDegrees = 90,
        speedMps = 0.5,
        activeRoute = plan.steps[2].path.drop(4),
        bagsCollected = 3,
        payloadKg = 3.0,
        binFillPercent = 55,
    )

    val telemetryWaiting = telemetryOnMission.copy(
        state = OperationalState.ESPERANDO_OPERADOR,
        waitReason = WaitReason.OBSTACULO,
        speedMps = 0.0,
        alerts = alerts,
        extraObstacles = setOf(Cell(8, 7)),
    )

    fun command(status: CommandStatus) = CommandRecord("c1", RobotCommand.Pause, NOW - 2_000, status)

    val missions = listOf(
        MissionRecord("m1", NOW - 600_000, NOW - 540_000, MissionResult.COMPLETADA, "B1", "T1", 21.5),
        MissionRecord("m2", NOW - 300_000, NOW - 280_000, MissionResult.BOLSA_JUNTO_AL_TACHO, "B2", "T2", 14.0),
    )

    val events = listOf(
        RobotEvent(NOW - 10_000, EventType.MISION_INICIADA, AlertSeverity.INFO, "B1 → T1", "m1"),
        RobotEvent(NOW - 5_000, EventType.ALERTA, AlertSeverity.ADVERTENCIA, "OBSTACULO", "m1"),
    )

    val missionSetupReady = MissionSetupUiState(
        site = site,
        bolsas = bolsas,
        tacho = tacho,
        preview = PlanPreview.Ready(plan.distanceMeters(site), 60, plan.steps.map { it.kind }),
        route = plan.steps.flatMap { it.path },
        telemetry = telemetryOnMission.copy(state = OperationalState.INACTIVO, mission = null, position = Position(0.75, 0.75, site.safePoint)),
        start = Availability.ENABLED,
    )
}
