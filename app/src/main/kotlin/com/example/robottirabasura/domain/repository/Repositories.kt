package com.example.robottirabasura.domain.repository

import com.example.robottirabasura.domain.model.CommandRecord
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.Telemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class RobotInfo(val id: String, val model: String, val alias: String, val simulated: Boolean)

/**
 * The only door from the UI to the robot. The app never talks to the simulator directly, so a real link (DDS bridge,
 * REST gateway) could replace the simulated implementation without touching ViewModels.
 */
interface RobotRepository {
    val availableRobots: List<RobotInfo>
    val site: SiteMap

    /** Robot currently selected, or null when the operator disconnected. */
    val connectedRobot: StateFlow<RobotInfo?>

    /** Last telemetry received. It is kept when the link drops: check [lastReceivedAtMillis] for its age. */
    val telemetry: StateFlow<Telemetry?>
    val lastReceivedAtMillis: StateFlow<Long?>

    /** Commands of this session, newest first, with their current status. */
    val commands: StateFlow<List<CommandRecord>>

    /** Events to persist in the history (missions, alerts, commands, connection). */
    val events: Flow<RobotEvent>

    /** A mission started or ended (same id, updated record). */
    val missionUpdates: Flow<MissionRecord>

    fun connect(robotId: String)
    fun disconnect()

    /** Sends a command and returns its id; follow its status in [commands]. */
    fun send(command: RobotCommand): String
}

/** Local history of missions and events (Room). */
interface HistoryRepository {
    fun observeEvents(limit: Int = 200): Flow<List<RobotEvent>>
    fun observeMissions(): Flow<List<MissionRecord>>
    suspend fun record(event: RobotEvent)
    suspend fun saveMission(mission: MissionRecord)
    suspend fun clear()
}

/** Wall clock, replaceable in tests. */
fun interface Clock {
    fun nowMillis(): Long
}

/**
 * Fault injection for the simulated robot (course requirement: force disconnections and rejections). Only the
 * simulator screen uses it; operator screens never see it.
 */
interface SimulatorControls {
    val linkUp: StateFlow<Boolean>
    fun setLinkUp(up: Boolean)
    /** The next command reaches the robot but its answer is lost: it ends as "sin respuesta". */
    fun dropNextResponse()
    fun rejectNextCommand()
    /** Puts an obstacle on the route ahead. False when the robot isn't navigating. */
    fun injectObstacleAhead(): Boolean
    fun clearObstacles()
    fun setBinFull(full: Boolean)
    fun setBattery(percent: Int)
    fun setPersonNearby(nearby: Boolean)
}
