package com.example.robottirabasura.data.sim

import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.CommandRecord
import com.example.robottirabasura.domain.model.CommandResponse
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.EventType
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.repository.Clock
import com.example.robottirabasura.domain.repository.RobotInfo
import com.example.robottirabasura.domain.repository.RobotRepository
import com.example.robottirabasura.domain.repository.SimulatorControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [RobotRepository] backed by an in-process [RobotSimulator]. It adds what a real link would have: latency, a
 * response timeout, telemetry that stops arriving when the link drops, and events buffered until it comes back.
 */
class SimulatedRobotRepository(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val newId: () -> String,
    override val site: SiteMap = SiteMap.DEMO,
) : RobotRepository, SimulatorControls {

    companion object {
        const val TICK_MILLIS = 200L
        const val PUBLISH_EVERY_MILLIS = 500L
        const val RESPONSE_LATENCY_MILLIS = 600L
        const val RESPONSE_TIMEOUT_MILLIS = 5_000L
        private const val MAX_COMMANDS = 30
        val G1 = RobotInfo(id = "g1-sim-01", model = "Unitree G1 EDU (simulado)", alias = "G1 · Patio", simulated = true)
    }

    override val availableRobots: List<RobotInfo> = listOf(G1)

    private val lock = Any()
    private var simulator: RobotSimulator? = null
    private var loop: Job? = null
    private var lastPublishedAt = 0L
    private var dropNext = false

    private val _connectedRobot = MutableStateFlow<RobotInfo?>(null)
    override val connectedRobot: StateFlow<RobotInfo?> = _connectedRobot.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    override val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    private val _lastReceivedAt = MutableStateFlow<Long?>(null)
    override val lastReceivedAtMillis: StateFlow<Long?> = _lastReceivedAt.asStateFlow()

    private val _commands = MutableStateFlow<List<CommandRecord>>(emptyList())
    override val commands: StateFlow<List<CommandRecord>> = _commands.asStateFlow()

    private val _events = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 128)
    override val events: Flow<RobotEvent> = _events.asSharedFlow()

    private val _missionUpdates = MutableSharedFlow<MissionRecord>(extraBufferCapacity = 16)
    override val missionUpdates: Flow<MissionRecord> = _missionUpdates.asSharedFlow()

    private val _linkUp = MutableStateFlow(true)
    override val linkUp: StateFlow<Boolean> = _linkUp.asStateFlow()

    override fun connect(robotId: String) {
        val robot = availableRobots.firstOrNull { it.id == robotId } ?: return
        if (_connectedRobot.value?.id == robotId && loop?.isActive == true) return
        disconnect()
        val sim = RobotSimulator(site, robot.id, newId)
        synchronized(lock) { simulator = sim }
        _connectedRobot.value = robot
        _events.tryEmit(RobotEvent(clock.nowMillis(), EventType.CONEXION, AlertSeverity.INFO, "Conectado a ${robot.alias}"))
        publish(clock.nowMillis())
        loop = scope.launch {
            var last = clock.nowMillis()
            while (isActive) {
                delay(TICK_MILLIS)
                val now = clock.nowMillis()
                synchronized(lock) { sim.tick(now - last, now) }
                last = now
                if (_linkUp.value) {
                    flushRobotOutputs(sim)
                    if (now - lastPublishedAt >= PUBLISH_EVERY_MILLIS) publish(now)
                }
            }
        }
    }

    override fun disconnect() {
        loop?.cancel()
        loop = null
        val robot = _connectedRobot.value ?: return
        synchronized(lock) { simulator = null }
        _connectedRobot.value = null
        _telemetry.value = null
        _lastReceivedAt.value = null
        _commands.value = emptyList()
        _events.tryEmit(RobotEvent(clock.nowMillis(), EventType.CONEXION, AlertSeverity.INFO, "Desconectado de ${robot.alias}"))
    }

    override fun send(command: RobotCommand): String {
        val id = newId()
        val sentAt = clock.nowMillis()
        _commands.update { (listOf(CommandRecord(id, command, sentAt, CommandStatus.PENDIENTE)) + it).take(MAX_COMMANDS) }
        scope.launch {
            delay(RESPONSE_LATENCY_MILLIS)
            val sim = synchronized(lock) { simulator }
            val delivered = _linkUp.value && sim != null
            val answerLost = dropNext.also { dropNext = false }
            // A lost answer doesn't mean the robot didn't act: it may have executed the order anyway.
            val response = if (delivered) synchronized(lock) { sim.handle(command, clock.nowMillis()) } else null
            if (response == null || answerLost) {
                delay(RESPONSE_TIMEOUT_MILLIS - RESPONSE_LATENCY_MILLIS)
                updateCommand(id) { it.copy(status = CommandStatus.SIN_RESPUESTA, answeredAtMillis = clock.nowMillis()) }
                _events.tryEmit(
                    RobotEvent(clock.nowMillis(), EventType.COMANDO, AlertSeverity.ADVERTENCIA, "${command.type.name}: sin respuesta")
                )
                return@launch
            }
            val now = clock.nowMillis()
            when (response) {
                CommandResponse.Accepted -> updateCommand(id) { it.copy(status = CommandStatus.CONFIRMADA, answeredAtMillis = now) }
                is CommandResponse.Rejected -> updateCommand(id) {
                    it.copy(status = CommandStatus.RECHAZADA, answeredAtMillis = now, rejectReason = response.reason)
                }
            }
            val detail = when (response) {
                CommandResponse.Accepted -> "${command.type.name}: confirmada"
                is CommandResponse.Rejected -> "${command.type.name}: rechazada (${response.reason.name})"
            }
            val severity = if (response is CommandResponse.Rejected) AlertSeverity.ADVERTENCIA else AlertSeverity.INFO
            _events.tryEmit(RobotEvent(now, EventType.COMANDO, severity, detail))
            // The answer travels with fresh telemetry so the UI never shows "confirmed" with the old state.
            if (sim != null) flushRobotOutputs(sim)
            publish(now)
        }
        return id
    }

    private fun updateCommand(id: String, change: (CommandRecord) -> CommandRecord) {
        _commands.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun publish(now: Long) {
        val sim = synchronized(lock) { simulator } ?: return
        if (!_linkUp.value) return
        val signal = 70 + ((now / 1000) % 25).toInt()
        _telemetry.value = synchronized(lock) { sim.snapshot(now, signal) }
        _lastReceivedAt.value = now
        lastPublishedAt = now
    }

    private fun flushRobotOutputs(sim: RobotSimulator) {
        val (events, missions) = synchronized(lock) { sim.drainEvents() to sim.drainMissionRecords() }
        events.forEach { _events.tryEmit(it) }
        missions.forEach { _missionUpdates.tryEmit(it) }
    }

    // region SimulatorControls

    override fun setLinkUp(up: Boolean) {
        if (_linkUp.value == up) return
        _linkUp.value = up
        val now = clock.nowMillis()
        if (up) {
            _events.tryEmit(RobotEvent(now, EventType.CONEXION, AlertSeverity.INFO, "Enlace recuperado"))
            synchronized(lock) { simulator }?.let { flushRobotOutputs(it) }
            publish(now)
        } else {
            _events.tryEmit(RobotEvent(now, EventType.CONEXION, AlertSeverity.ADVERTENCIA, "Enlace perdido"))
        }
    }

    override fun dropNextResponse() { dropNext = true }

    override fun rejectNextCommand() = withSimulator { it.rejectNextCommand() }

    override fun injectObstacleAhead(): Boolean = synchronized(lock) { simulator?.injectObstacleAhead() ?: false }

    override fun clearObstacles() = withSimulator { it.clearObstacles() }

    override fun setBinFull(full: Boolean) = withSimulator { it.setBinFull(full) }

    override fun setBattery(percent: Int) = withSimulator { it.setBattery(percent.toDouble()) }

    override fun setPersonNearby(nearby: Boolean) = withSimulator { it.setPersonNearby(nearby, clock.nowMillis()) }

    private fun withSimulator(action: (RobotSimulator) -> Unit) {
        synchronized(lock) { simulator?.let(action) }
        publish(clock.nowMillis())
    }

    // endregion
}
