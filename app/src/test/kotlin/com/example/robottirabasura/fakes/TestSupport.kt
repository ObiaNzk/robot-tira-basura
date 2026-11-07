package com.example.robottirabasura.fakes

import com.example.robottirabasura.domain.model.CommandRecord
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.repository.Clock
import com.example.robottirabasura.domain.repository.RobotInfo
import com.example.robottirabasura.domain.repository.RobotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeClock(var now: Long = 1_000_000L) : Clock {
    override fun nowMillis() = now
}

fun sequentialIds(): () -> String {
    var n = 0
    return { "id-${++n}" }
}

/** Repository whose state the test sets by hand; records what was sent. */
class FakeRobotRepository(override val site: SiteMap = SiteMap.DEMO) : RobotRepository {
    val robot = RobotInfo("g1", "G1", "G1 · Test", simulated = true)
    override val availableRobots = listOf(robot)
    override val connectedRobot = MutableStateFlow<RobotInfo?>(robot)
    override val telemetry = MutableStateFlow<Telemetry?>(null)
    override val lastReceivedAtMillis = MutableStateFlow<Long?>(null)
    override val commands = MutableStateFlow<List<CommandRecord>>(emptyList())
    override val events = MutableSharedFlow<RobotEvent>()
    override val missionUpdates = MutableSharedFlow<MissionRecord>()
    val sent = mutableListOf<RobotCommand>()
    private val ids = sequentialIds()

    override fun connect(robotId: String) { connectedRobot.value = robot }
    override fun disconnect() { connectedRobot.value = null }

    override fun send(command: RobotCommand): String {
        sent += command
        val id = ids()
        commands.update { listOf(CommandRecord(id, command, 0, CommandStatus.PENDIENTE)) + it }
        return id
    }

    fun answer(id: String, status: CommandStatus) {
        commands.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }
}
