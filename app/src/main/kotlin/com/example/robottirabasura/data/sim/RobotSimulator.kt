package com.example.robottirabasura.data.sim

import com.example.robottirabasura.domain.model.Alert
import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.AlertType
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.CommandResponse
import com.example.robottirabasura.domain.model.EventType
import com.example.robottirabasura.domain.model.MissionPlan
import com.example.robottirabasura.domain.model.MissionProgress
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.MissionResult
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.OperationalState.CARGANDO
import com.example.robottirabasura.domain.model.OperationalState.DETENIDO
import com.example.robottirabasura.domain.model.OperationalState.EN_MISION
import com.example.robottirabasura.domain.model.OperationalState.ESPERANDO_OPERADOR
import com.example.robottirabasura.domain.model.OperationalState.INACTIVO
import com.example.robottirabasura.domain.model.OperationalState.PAUSADO
import com.example.robottirabasura.domain.model.OperationalState.REGRESANDO
import com.example.robottirabasura.domain.model.Position
import com.example.robottirabasura.domain.model.RejectReason
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepKind
import com.example.robottirabasura.domain.model.StepStatus
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.model.WaitReason
import com.example.robottirabasura.domain.planning.MissionPlanner
import com.example.robottirabasura.domain.planning.PathPlanner
import com.example.robottirabasura.domain.planning.PlanResult
import com.example.robottirabasura.domain.rules.CommandRules
import kotlin.math.roundToInt

/**
 * Simulated Unitree G1 doing the trash macro. A plain state machine with no threads or clocks: the caller advances it
 * with [tick] and sends commands with [handle], so tests drive it step by step. Values are plausible, not measured.
 */
class RobotSimulator(
    private val map: SiteMap,
    private val robotId: String,
    private val newId: () -> String,
    initialBattery: Double = 82.0,
) {
    companion object {
        const val CELLS_PER_SECOND = 1.0
        const val CELLS_PER_SECOND_NEAR_PEOPLE = 0.5
        const val LOW_BATTERY = 20.0
        const val CRITICAL_BATTERY = 8.0
        const val INITIAL_BIN_FILL = 40
        const val BIN_FILL_PER_BAG = 15
        private const val CHARGE_PER_SECOND = 0.8
        private const val DRAIN_IDLE = 0.01
        private const val DRAIN_HOLD = 0.02
        private const val DRAIN_WALKING = 0.12
        private const val DRAIN_MANIPULATING = 0.2
        private const val MAX_ALERTS = 30
    }

    private class ActiveMission(
        val id: String,
        var plan: MissionPlan,
        val statuses: MutableList<StepStatus>,
        val startedAtMillis: Long,
        var current: Int = 0,
        var elapsedMillis: Long = 0,
        var leftBesideBin: Boolean = false,
        var walkedCells: Int = 0,
        var result: MissionResult? = null,
    )

    var state: OperationalState = INACTIVO
        private set
    private var waitReason: WaitReason? = null
    /** State to go back to after a pause, a stop or an operator decision. */
    private var resumeState: OperationalState? = null

    private var battery = initialBattery
    private var cell = map.safePoint
    private var path: List<Cell> = emptyList()
    private var pathIndex = 0
    private var fraction = 0.0
    private var heading = 90
    private var speedCellsPerSecond = 0.0

    private var mission: ActiveMission? = null
    private val alerts = mutableListOf<Alert>()
    private val obstacles = mutableSetOf<Cell>()
    private var bagsCollected = 0
    private var payloadKg = 0.0
    private var binFill = INITIAL_BIN_FILL
    private var personNearby = false
    private var rejectNext = false

    private val pendingEvents = mutableListOf<RobotEvent>()
    private val pendingMissionRecords = mutableListOf<MissionRecord>()

    // region Outputs

    fun snapshot(nowMillis: Long, signalPercent: Int): Telemetry = Telemetry(
        robotId = robotId,
        timestampMillis = nowMillis,
        signalPercent = signalPercent,
        batteryPercent = battery,
        state = state,
        waitReason = waitReason,
        mission = mission?.let { m ->
            MissionProgress(m.id, m.plan, m.statuses.toList(), m.current, progressPercent(m), m.result)
        },
        position = position(),
        headingDegrees = heading,
        speedMps = speedCellsPerSecond * map.cellSizeMeters,
        alerts = alerts.toList(),
        extraObstacles = obstacles.toSet(),
        activeRoute = if (state == EN_MISION || state == REGRESANDO || state == PAUSADO || state == DETENIDO ||
            state == ESPERANDO_OPERADOR) path.drop(pathIndex) else emptyList(),
        bagsCollected = bagsCollected,
        payloadKg = payloadKg,
        binFillPercent = binFill,
    )

    fun drainEvents(): List<RobotEvent> = pendingEvents.toList().also { pendingEvents.clear() }
    fun drainMissionRecords(): List<MissionRecord> = pendingMissionRecords.toList().also { pendingMissionRecords.clear() }

    // endregion

    // region Fault injection

    fun rejectNextCommand() { rejectNext = true }

    fun setBattery(percent: Double) { battery = percent.coerceIn(0.0, 100.0) }

    fun setBinFull(full: Boolean) { binFill = if (full) 100 else INITIAL_BIN_FILL }

    fun setPersonNearby(nearby: Boolean, nowMillis: Long) {
        personNearby = nearby
        if (nearby) raise(AlertType.PERSONA_CERCA, AlertSeverity.INFO, nowMillis)
    }

    fun clearObstacles() { obstacles.clear() }

    fun injectObstacleAhead(): Boolean {
        val navigating = state == REGRESANDO || (state == EN_MISION && currentStepKind()?.isNavigation == true)
        if (!navigating) return false
        // Never on the goal itself: the operator must be able to go around it.
        val candidate = (pathIndex + 2 until path.lastIndex).firstOrNull() ?: (pathIndex + 1).takeIf { it < path.lastIndex }
        candidate ?: return false
        obstacles += path[candidate]
        return true
    }

    // endregion

    // region Commands

    fun handle(command: RobotCommand, nowMillis: Long): CommandResponse {
        if (rejectNext) {
            rejectNext = false
            return reject(RejectReason.RECHAZO_SIMULADO)
        }
        return when (command) {
            is RobotCommand.StartMission -> start(command, nowMillis)
            RobotCommand.Pause -> {
                if (state != EN_MISION && state != REGRESANDO) return reject(RejectReason.ESTADO_INVALIDO)
                resumeState = state
                state = PAUSADO
                speedCellsPerSecond = 0.0
                CommandResponse.Accepted
            }
            RobotCommand.Resume -> resume()
            RobotCommand.Cancel -> {
                val m = mission?.takeIf { it.result == null } ?: return reject(RejectReason.ESTADO_INVALIDO)
                if (state !in setOf(EN_MISION, PAUSADO, DETENIDO, ESPERANDO_OPERADOR)) return reject(RejectReason.ESTADO_INVALIDO)
                finish(m, MissionResult.CANCELADA, nowMillis)
                goIdle()
                CommandResponse.Accepted
            }
            RobotCommand.ReturnToSafePoint -> returnToSafePoint(nowMillis)
            RobotCommand.OperationalStop -> {
                if (state !in setOf(EN_MISION, REGRESANDO, PAUSADO, ESPERANDO_OPERADOR)) return reject(RejectReason.ESTADO_INVALIDO)
                if (state != PAUSADO) resumeState = state
                state = DETENIDO
                speedCellsPerSecond = 0.0
                CommandResponse.Accepted
            }
            is RobotCommand.AcknowledgeAlert -> {
                val index = alerts.indexOfFirst { it.id == command.alertId }
                if (index < 0) return reject(RejectReason.ALERTA_INEXISTENTE)
                alerts[index] = alerts[index].copy(acknowledged = true)
                event(nowMillis, EventType.ALERTA_RECONOCIDA, AlertSeverity.INFO, alerts[index].type.name)
                CommandResponse.Accepted
            }
            RobotCommand.Replan -> replan()
            RobotCommand.LeaveBesideBin -> leaveBesideBin(nowMillis)
        }
    }

    private fun start(command: RobotCommand.StartMission, nowMillis: Long): CommandResponse {
        if (state != INACTIVO && state != CARGANDO) return reject(RejectReason.ESTADO_INVALIDO)
        if (battery < CommandRules.MIN_BATTERY_TO_START) return reject(RejectReason.BATERIA_INSUFICIENTE)
        if (alerts.any { !it.acknowledged && it.severity == AlertSeverity.CRITICA }) {
            return reject(RejectReason.ALERTA_CRITICA_PENDIENTE)
        }
        val plan = when (val result = MissionPlanner.plan(map, cell, command.bolsas, command.tacho, obstacles)) {
            is PlanResult.Ready -> result.plan
            else -> return reject(RejectReason.SIN_RUTA)
        }
        val m = ActiveMission(newId(), plan, MutableList(plan.steps.size) { StepStatus.PENDIENTE }, nowMillis)
        mission = m
        state = EN_MISION
        waitReason = null
        resumeState = null
        event(nowMillis, EventType.MISION_INICIADA, AlertSeverity.INFO, "${command.bolsas.id} → ${command.tacho.id}", m.id)
        pendingMissionRecords += record(m, nowMillis = null)
        startStep(m)
        return CommandResponse.Accepted
    }

    private fun resume(): CommandResponse = when {
        state == PAUSADO || state == DETENIDO -> {
            if (battery <= CRITICAL_BATTERY) {
                reject(RejectReason.BATERIA_INSUFICIENTE)
            } else {
                state = resumeState ?: INACTIVO
                resumeState = null
                CommandResponse.Accepted
            }
        }
        state == ESPERANDO_OPERADOR && waitReason == WaitReason.TACHO_LLENO -> {
            // Try lifting the lid again (someone may have emptied the bin).
            waitReason = null
            state = EN_MISION
            mission?.elapsedMillis = 0
            CommandResponse.Accepted
        }
        else -> reject(RejectReason.ESTADO_INVALIDO)
    }

    private fun returnToSafePoint(nowMillis: Long): CommandResponse {
        if (state == REGRESANDO) return reject(RejectReason.ESTADO_INVALIDO)
        if ((state == INACTIVO || state == CARGANDO) && cell == map.safePoint) return reject(RejectReason.ESTADO_INVALIDO)
        val route = PathPlanner.findPath(map, cell, map.safePoint, obstacles) ?: return reject(RejectReason.SIN_RUTA)
        mission?.takeIf { it.result == null }?.let { finish(it, MissionResult.ABORTADA_REGRESO, nowMillis) }
        follow(route)
        state = REGRESANDO
        waitReason = null
        resumeState = null
        return CommandResponse.Accepted
    }

    private fun replan(): CommandResponse {
        if (state != ESPERANDO_OPERADOR || waitReason != WaitReason.OBSTACULO) return reject(RejectReason.ESTADO_INVALIDO)
        val goal = path.lastOrNull() ?: return reject(RejectReason.SIN_RUTA)
        val route = PathPlanner.findPath(map, cell, goal, obstacles) ?: return reject(RejectReason.SIN_RUTA)
        follow(route)
        mission?.takeIf { state == ESPERANDO_OPERADOR && resumeState == EN_MISION }?.let { m ->
            m.plan = m.plan.copy(steps = m.plan.steps.toMutableList().also { it[m.current] = it[m.current].copy(path = route) })
        }
        state = resumeState ?: INACTIVO
        resumeState = null
        waitReason = null
        return CommandResponse.Accepted
    }

    private fun leaveBesideBin(nowMillis: Long): CommandResponse {
        val m = mission
        if (m == null || state != ESPERANDO_OPERADOR || waitReason != WaitReason.TACHO_LLENO) {
            return reject(RejectReason.ESTADO_INVALIDO)
        }
        payloadKg = 0.0
        m.leftBesideBin = true
        val returnIndex = m.plan.steps.indexOfFirst { it.kind == StepKind.REGRESAR_A_BASE }
        for (i in m.current until returnIndex) m.statuses[i] = StepStatus.OMITIDO
        m.current = returnIndex
        state = EN_MISION
        waitReason = null
        event(nowMillis, EventType.PASO_COMPLETADO, AlertSeverity.ADVERTENCIA, "Bolsa dejada junto al tacho", m.id)
        startStep(m)
        return CommandResponse.Accepted
    }

    private fun reject(reason: RejectReason) = CommandResponse.Rejected(reason)

    // endregion

    // region Time

    fun tick(deltaMillis: Long, nowMillis: Long) {
        val seconds = deltaMillis / 1000.0
        updateBattery(seconds, nowMillis)
        when (state) {
            EN_MISION -> advanceMission(deltaMillis, nowMillis)
            REGRESANDO -> when (advance(seconds)) {
                Move.ARRIVED -> {
                    event(nowMillis, EventType.MISION_FINALIZADA, AlertSeverity.INFO, "En punto seguro", mission?.id)
                    goIdle()
                }
                Move.BLOCKED -> waitForOperator(WaitReason.OBSTACULO, nowMillis)
                Move.MOVING -> Unit
            }
            else -> speedCellsPerSecond = 0.0
        }
    }

    private fun updateBattery(seconds: Double, nowMillis: Long) {
        val atBase = cell == map.safePoint
        battery = when {
            (state == INACTIVO || state == CARGANDO) && atBase -> (battery + CHARGE_PER_SECOND * seconds).coerceAtMost(100.0)
            state == EN_MISION && currentStepKind()?.isNavigation == false -> battery - DRAIN_MANIPULATING * seconds
            state == EN_MISION || state == REGRESANDO -> battery - DRAIN_WALKING * seconds
            state == INACTIVO -> battery - DRAIN_IDLE * seconds
            else -> battery - DRAIN_HOLD * seconds
        }.coerceIn(0.0, 100.0)
        if (state == INACTIVO && atBase && battery < 100.0) state = CARGANDO
        if (state == CARGANDO && (battery >= 100.0 || !atBase)) state = INACTIVO

        if (battery < LOW_BATTERY) raise(AlertType.BATERIA_BAJA, AlertSeverity.ADVERTENCIA, nowMillis)
        // The robot stops by itself once, when the critical alert is first raised; after that the operator decides
        // (for example, walking it back to the charger).
        if (battery <= CRITICAL_BATTERY && raise(AlertType.BATERIA_CRITICA, AlertSeverity.CRITICA, nowMillis)) {
            if (state == EN_MISION || state == REGRESANDO) {
                resumeState = state
                state = DETENIDO
                speedCellsPerSecond = 0.0
            }
        }
    }

    private fun advanceMission(deltaMillis: Long, nowMillis: Long) {
        val m = mission ?: return goIdle()
        val step = m.plan.steps[m.current]
        if (step.kind.isNavigation) {
            when (advance(deltaMillis / 1000.0)) {
                Move.ARRIVED -> completeStep(m, nowMillis)
                Move.BLOCKED -> waitForOperator(WaitReason.OBSTACULO, nowMillis)
                Move.MOVING -> Unit
            }
            return
        }
        speedCellsPerSecond = 0.0
        m.elapsedMillis += deltaMillis
        if (m.elapsedMillis < step.durationMillis) return
        when (step.kind) {
            StepKind.RECOGER_BOLSA -> payloadKg = 2.5 + (bagsCollected % 4) * 0.5
            StepKind.LEVANTAR_TAPA -> if (binFill >= 100) {
                m.elapsedMillis = 0
                return waitForOperator(WaitReason.TACHO_LLENO, nowMillis)
            }
            StepKind.DEPOSITAR_BOLSA -> {
                payloadKg = 0.0
                bagsCollected++
                binFill = (binFill + BIN_FILL_PER_BAG).coerceAtMost(100)
            }
            else -> Unit
        }
        completeStep(m, nowMillis)
    }

    private fun completeStep(m: ActiveMission, nowMillis: Long) {
        m.statuses[m.current] = StepStatus.COMPLETADO
        event(nowMillis, EventType.PASO_COMPLETADO, AlertSeverity.INFO, m.plan.steps[m.current].kind.name, m.id)
        m.current++
        if (m.current >= m.plan.steps.size) {
            m.current = m.plan.steps.lastIndex
            finish(m, if (m.leftBesideBin) MissionResult.BOLSA_JUNTO_AL_TACHO else MissionResult.COMPLETADA, nowMillis)
            goIdle()
        } else {
            startStep(m)
        }
    }

    private fun startStep(m: ActiveMission) {
        m.statuses[m.current] = StepStatus.EN_CURSO
        m.elapsedMillis = 0
        val step = m.plan.steps[m.current]
        if (!step.kind.isNavigation) return
        // The planned route started where the previous step ended; recompute from where the robot really is.
        val goal = step.path.last()
        val route = PathPlanner.findPath(map, cell, goal, obstacles) ?: step.path
        m.plan = m.plan.copy(steps = m.plan.steps.toMutableList().also { it[m.current] = step.copy(path = route) })
        follow(route)
    }

    private fun finish(m: ActiveMission, result: MissionResult, nowMillis: Long) {
        m.result = result
        for (i in m.statuses.indices) if (m.statuses[i] != StepStatus.COMPLETADO) m.statuses[i] = StepStatus.OMITIDO
        val severity = if (result == MissionResult.COMPLETADA) AlertSeverity.INFO else AlertSeverity.ADVERTENCIA
        event(nowMillis, EventType.MISION_FINALIZADA, severity, result.name, m.id)
        pendingMissionRecords += record(m, nowMillis)
    }

    /** While running: planned distance. Once finished: distance actually walked (detours included). */
    private fun record(m: ActiveMission, nowMillis: Long?) = MissionRecord(
        id = m.id,
        startedAtMillis = m.startedAtMillis,
        endedAtMillis = nowMillis,
        result = m.result,
        bolsasId = m.plan.bolsas.id,
        tachoId = m.plan.tacho.id,
        distanceMeters = if (nowMillis == null) m.plan.distanceMeters(map) else m.walkedCells * map.cellSizeMeters,
    )

    private fun waitForOperator(reason: WaitReason, nowMillis: Long) {
        resumeState = state
        state = ESPERANDO_OPERADOR
        waitReason = reason
        speedCellsPerSecond = 0.0
        val type = if (reason == WaitReason.OBSTACULO) AlertType.OBSTACULO else AlertType.TACHO_LLENO
        raise(type, AlertSeverity.ADVERTENCIA, nowMillis, force = true)
    }

    private fun goIdle() {
        state = INACTIVO
        waitReason = null
        resumeState = null
        speedCellsPerSecond = 0.0
        path = emptyList()
        pathIndex = 0
        fraction = 0.0
    }

    // endregion

    // region Navigation

    private enum class Move { MOVING, ARRIVED, BLOCKED }

    private fun follow(route: List<Cell>) {
        path = route
        pathIndex = 0
        fraction = 0.0
        cell = route.first()
    }

    private fun advance(seconds: Double): Move {
        speedCellsPerSecond = if (personNearby) CELLS_PER_SECOND_NEAR_PEOPLE else CELLS_PER_SECOND
        var budget = seconds * speedCellsPerSecond
        while (budget > 0 && pathIndex < path.lastIndex) {
            val next = path[pathIndex + 1]
            if (next in obstacles) {
                fraction = 0.0
                speedCellsPerSecond = 0.0
                return Move.BLOCKED
            }
            heading = headingTo(path[pathIndex], next)
            val needed = 1.0 - fraction
            if (budget >= needed) {
                budget -= needed
                pathIndex++
                fraction = 0.0
                cell = path[pathIndex]
                mission?.takeIf { it.result == null }?.let { it.walkedCells++ }
            } else {
                fraction += budget
                budget = 0.0
            }
        }
        return if (pathIndex >= path.lastIndex) Move.ARRIVED else Move.MOVING
    }

    private fun headingTo(from: Cell, to: Cell): Int = when {
        to.x > from.x -> 90
        to.x < from.x -> 270
        to.y > from.y -> 180
        else -> 0
    }

    private fun position(): Position {
        val next = path.getOrNull(pathIndex + 1)
        val x = cell.x + if (next != null) (next.x - cell.x) * fraction else 0.0
        val y = cell.y + if (next != null) (next.y - cell.y) * fraction else 0.0
        val size = map.cellSizeMeters
        return Position(xMeters = (x + 0.5) * size, yMeters = (y + 0.5) * size, cell = cell)
    }

    // endregion

    private fun currentStepKind(): StepKind? = mission?.takeIf { it.result == null }?.let { it.plan.steps[it.current].kind }

    private fun progressPercent(m: ActiveMission): Int {
        if (m.result == MissionResult.COMPLETADA || m.result == MissionResult.BOLSA_JUNTO_AL_TACHO) return 100
        // Weight: one per route cell, one per second of manipulation (both take about a second at normal speed).
        fun weight(i: Int): Double = m.plan.steps[i].let { s ->
            if (s.kind.isNavigation) (s.path.size - 1).coerceAtLeast(0).toDouble() else s.durationMillis / 1000.0
        }
        val total = m.plan.steps.indices.sumOf { weight(it) }.takeIf { it > 0 } ?: return 0
        var done = m.statuses.indices.filter { m.statuses[it] == StepStatus.COMPLETADO || m.statuses[it] == StepStatus.OMITIDO && m.result == null }
            .sumOf { weight(it) }
        if (m.result == null && m.current in m.plan.steps.indices && m.statuses[m.current] == StepStatus.EN_CURSO) {
            val step = m.plan.steps[m.current]
            done += if (step.kind.isNavigation) pathIndex + fraction
            else (m.elapsedMillis.toDouble() / step.durationMillis).coerceAtMost(1.0) * weight(m.current)
        }
        return (done / total * 100).roundToInt().coerceIn(0, 100)
    }

    /** Raises an alert and returns true, or returns false when an equivalent one is already open. */
    private fun raise(type: AlertType, severity: AlertSeverity, nowMillis: Long, force: Boolean = false): Boolean {
        // One open alert per type: the same condition doesn't flood the list every tick.
        if (!force && alerts.any { it.type == type && !it.acknowledged }) return false
        // Battery alerts re-arm only after the battery recovered.
        val isBattery = type == AlertType.BATERIA_BAJA || type == AlertType.BATERIA_CRITICA
        if (!force && isBattery && alerts.any { it.type == type } && battery < LOW_BATTERY + 5) return false
        alerts += Alert(newId(), type, severity, nowMillis)
        if (alerts.size > MAX_ALERTS) alerts.removeAt(0)
        event(nowMillis, EventType.ALERTA, severity, type.name, mission?.takeIf { it.result == null }?.id)
        return true
    }

    private fun event(nowMillis: Long, type: EventType, severity: AlertSeverity, detail: String, missionId: String? = null) {
        pendingEvents += RobotEvent(nowMillis, type, severity, detail, missionId)
    }
}
