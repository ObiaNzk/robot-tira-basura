package com.example.robottirabasura.data

import com.example.robottirabasura.data.sim.SimulatedRobotRepository
import com.example.robottirabasura.data.sim.SimulatedRobotRepository.Companion.RESPONSE_LATENCY_MILLIS
import com.example.robottirabasura.data.sim.SimulatedRobotRepository.Companion.RESPONSE_TIMEOUT_MILLIS
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.Freshness
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.domain.model.RejectReason
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.repository.Clock
import com.example.robottirabasura.fakes.sequentialIds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedRobotRepositoryTest {
    private val start = RobotCommand.StartMission(
        QrTarget(QrKind.BOLSAS, "B1", Cell(1, 9)), QrTarget(QrKind.TACHO, "T1", Cell(16, 1)),
    )

    private fun TestScope.repository(): SimulatedRobotRepository {
        val clock = Clock { testScheduler.currentTime }
        return SimulatedRobotRepository(backgroundScope, clock, sequentialIds()).also {
            it.connect(SimulatedRobotRepository.G1.id)
        }
    }

    private fun SimulatedRobotRepository.status(id: String) = commands.value.single { it.id == id }.status

    @Test
    fun `a command is pending until the robot answers, then confirmed`() = runTest {
        val repo = repository()
        val id = repo.send(start)
        assertEquals(CommandStatus.PENDIENTE, repo.status(id))
        advanceTimeBy(RESPONSE_LATENCY_MILLIS + 1)
        assertEquals(CommandStatus.CONFIRMADA, repo.status(id))
        assertEquals(OperationalState.EN_MISION, repo.telemetry.value!!.state)
    }

    @Test
    fun `a rejected command shows the reason`() = runTest {
        val repo = repository()
        val id = repo.send(RobotCommand.Pause)
        advanceTimeBy(RESPONSE_LATENCY_MILLIS + 1)
        val record = repo.commands.value.single { it.id == id }
        assertEquals(CommandStatus.RECHAZADA, record.status)
        assertEquals(RejectReason.ESTADO_INVALIDO, record.rejectReason)
    }

    @Test
    fun `scenario 3 - without link telemetry stops, orders end without answer, and it recovers`() = runTest {
        val repo = repository()
        advanceTimeBy(1_000)
        repo.setLinkUp(false)
        val lastSeen = repo.lastReceivedAtMillis.value!!
        val id = repo.send(start)
        advanceTimeBy(RESPONSE_TIMEOUT_MILLIS + 1)

        assertEquals(CommandStatus.SIN_RESPUESTA, repo.status(id))
        assertEquals(lastSeen, repo.lastReceivedAtMillis.value)
        assertEquals(LinkState.DATOS_ANTIGUOS, Freshness.linkState(lastSeen, testScheduler.currentTime))
        // The order never reached the robot: it is still idle.
        repo.setLinkUp(true)
        assertTrue(repo.lastReceivedAtMillis.value!! > lastSeen)
        assertTrue(repo.telemetry.value!!.state in setOf(OperationalState.INACTIVO, OperationalState.CARGANDO))
    }

    @Test
    fun `a lost answer ends without response although the robot executed it`() = runTest {
        val repo = repository()
        repo.dropNextResponse()
        val id = repo.send(start)
        advanceTimeBy(RESPONSE_TIMEOUT_MILLIS + 1)
        assertEquals(CommandStatus.SIN_RESPUESTA, repo.status(id))
        assertEquals(OperationalState.EN_MISION, repo.telemetry.value!!.state)
    }

    @Test
    fun `disconnecting clears the session and connecting again starts fresh`() = runTest {
        val repo = repository()
        repo.disconnect()
        assertNull(repo.connectedRobot.value)
        assertNull(repo.telemetry.value)
        repo.connect(SimulatedRobotRepository.G1.id)
        assertNotNull(repo.telemetry.value)
    }
}
