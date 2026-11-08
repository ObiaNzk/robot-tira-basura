package com.example.robottirabasura.data

import com.example.robottirabasura.data.sim.RobotSimulator
import com.example.robottirabasura.domain.model.AlertType
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.CommandResponse
import com.example.robottirabasura.domain.model.EventType
import com.example.robottirabasura.domain.model.MissionResult
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.domain.model.RejectReason
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepKind
import com.example.robottirabasura.domain.model.StepStatus
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.model.WaitReason
import com.example.robottirabasura.fakes.sequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The five course scenarios (plus details) driven step by step on the simulator. */
class RobotSimulatorTest {
    private val map = SiteMap.DEMO
    private val bolsas = QrTarget(QrKind.BOLSAS, "B1", Cell(1, 9))
    private val tacho = QrTarget(QrKind.TACHO, "T1", Cell(16, 1))
    private val sim = RobotSimulator(map, "g1", sequentialIds(), initialBattery = 90.0)
    private var now = 0L

    private fun run(seconds: Int, step: Long = 200) {
        repeat((seconds * 1000 / step).toInt()) {
            now += step
            sim.tick(step, now)
        }
    }

    private fun runUntil(maxSeconds: Int = 300, condition: (Telemetry) -> Boolean): Telemetry {
        repeat(maxSeconds * 5) {
            now += 200
            sim.tick(200, now)
            val t = sim.snapshot(now, 90)
            if (condition(t)) return t
        }
        throw AssertionError("Condition not reached in $maxSeconds s; state=${sim.snapshot(now, 90).state}")
    }

    private fun t() = sim.snapshot(now, 90)

    private fun start() = assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.StartMission(bolsas, tacho), now))

    @Test
    fun `scenario 1 - normal mission runs every step, deposits the bag and ends at the safe point`() {
        start()
        assertEquals(OperationalState.EN_MISION, t().state)
        val end = runUntil { it.mission?.result != null }
        assertEquals(MissionResult.COMPLETADA, end.mission!!.result)
        assertEquals(100, end.mission!!.progressPercent)
        assertTrue(end.mission!!.stepStatuses.all { it == StepStatus.COMPLETADO })
        assertEquals(1, end.bagsCollected)
        assertEquals(0.0, end.payloadKg, 0.0)
        assertEquals(RobotSimulator.INITIAL_BIN_FILL + RobotSimulator.BIN_FILL_PER_BAG, end.binFillPercent)
        assertEquals(map.safePoint, end.position.cell)
        val events = sim.drainEvents().map { it.type }
        assertTrue(EventType.MISION_INICIADA in events && EventType.MISION_FINALIZADA in events)
        val records = sim.drainMissionRecords()
        assertEquals(listOf(null, MissionResult.COMPLETADA), records.map { it.result })
    }

    @Test
    fun `progress grows and the robot carries the bag between pick up and drop`() {
        start()
        runUntil { it.mission?.currentKind == StepKind.NAVEGAR_A_TACHO }
        run(1)
        val carrying = t()
        assertTrue(carrying.payloadKg > 0)
        assertTrue(carrying.mission!!.progressPercent in 1..99)
        assertTrue(carrying.speedMps > 0)
    }

    @Test
    fun `scenario 2 - low battery warns, and a critical battery stops the robot once`() {
        start()
        sim.setBattery(19.0)
        run(1)
        assertTrue(t().alerts.any { it.type == AlertType.BATERIA_BAJA })
        assertEquals(OperationalState.EN_MISION, t().state)

        sim.setBattery(8.0)
        run(1)
        assertEquals(OperationalState.DETENIDO, t().state)
        assertEquals(CommandResponse.Rejected(RejectReason.BATERIA_INSUFICIENTE), sim.handle(RobotCommand.Resume, now))
        // The available action: walk back to the charger. It must not stop again on the way.
        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.ReturnToSafePoint, now))
        val back = runUntil { it.state != OperationalState.REGRESANDO }
        assertEquals(map.safePoint, back.position.cell)
        assertEquals(MissionResult.ABORTADA_REGRESO, back.mission!!.result)
    }

    @Test
    fun `start is rejected with low battery`() {
        sim.setBattery(20.0)
        assertEquals(
            CommandResponse.Rejected(RejectReason.BATERIA_INSUFICIENTE),
            sim.handle(RobotCommand.StartMission(bolsas, tacho), now),
        )
    }

    @Test
    fun `robot charges at the safe point while idle`() {
        sim.setBattery(50.0)
        run(2)
        assertEquals(OperationalState.CARGANDO, t().state)
        assertTrue(t().batteryPercent > 50.0)
    }

    @Test
    fun `scenario 4 - an obstacle stops the robot until the operator decides to go around it`() {
        start()
        run(2)
        assertTrue(sim.injectObstacleAhead())
        val blocked = runUntil { it.state == OperationalState.ESPERANDO_OPERADOR }
        assertEquals(WaitReason.OBSTACULO, blocked.waitReason)
        assertEquals(0.0, blocked.speedMps, 0.0)
        assertTrue(blocked.alerts.any { it.type == AlertType.OBSTACULO && !it.acknowledged })
        val obstacle = blocked.extraObstacles.single()

        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.Replan, now))
        assertEquals(OperationalState.EN_MISION, t().state)
        assertTrue(obstacle !in t().activeRoute)
        val end = runUntil { it.mission?.result != null }
        assertEquals(MissionResult.COMPLETADA, end.mission!!.result)
        // The history keeps the whole distance walked, detour included, not just the last leg.
        val (started, finished) = sim.drainMissionRecords().let { it.first() to it.last() }
        assertTrue(finished.distanceMeters > started.distanceMeters)
    }

    @Test
    fun `scenario 5 - a full bin asks the operator, who leaves the bag beside it`() {
        sim.setBinFull(true)
        start()
        val waiting = runUntil { it.state == OperationalState.ESPERANDO_OPERADOR }
        assertEquals(WaitReason.TACHO_LLENO, waiting.waitReason)
        assertEquals(StepKind.LEVANTAR_TAPA, waiting.mission!!.currentKind)
        assertTrue(waiting.payloadKg > 0)

        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.LeaveBesideBin, now))
        val end = runUntil { it.mission?.result != null }
        assertEquals(MissionResult.BOLSA_JUNTO_AL_TACHO, end.mission!!.result)
        assertEquals(0, end.bagsCollected)
        assertEquals(0.0, end.payloadKg, 0.0)
        val statuses = end.mission!!.stepStatuses
        assertEquals(StepStatus.OMITIDO, statuses[StepKind.DEPOSITAR_BOLSA.ordinal])
        assertEquals(StepStatus.COMPLETADO, statuses[StepKind.REGRESAR_A_BASE.ordinal])
    }

    @Test
    fun `a full bin emptied meanwhile lets the robot retry and finish normally`() {
        sim.setBinFull(true)
        start()
        runUntil { it.state == OperationalState.ESPERANDO_OPERADOR }
        sim.setBinFull(false)
        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.Resume, now))
        assertEquals(MissionResult.COMPLETADA, runUntil { it.mission?.result != null }.mission!!.result)
    }

    @Test
    fun `pause holds position and resume continues`() {
        start()
        run(3)
        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.Pause, now))
        val held = t().position
        run(3)
        assertEquals(held, t().position)
        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.Resume, now))
        run(1)
        assertTrue(t().position != held)
    }

    @Test
    fun `operational stop freezes the robot and cancel ends the mission where it is`() {
        start()
        run(3)
        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.OperationalStop, now))
        assertEquals(OperationalState.DETENIDO, t().state)
        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.Cancel, now))
        assertEquals(OperationalState.INACTIVO, t().state)
        assertEquals(MissionResult.CANCELADA, t().mission!!.result)
        assertFalse(t().isOnMission)
    }

    @Test
    fun `invalid commands are rejected without changing anything`() {
        assertEquals(CommandResponse.Rejected(RejectReason.ESTADO_INVALIDO), sim.handle(RobotCommand.Pause, now))
        assertEquals(CommandResponse.Rejected(RejectReason.ESTADO_INVALIDO), sim.handle(RobotCommand.ReturnToSafePoint, now))
        assertEquals(CommandResponse.Rejected(RejectReason.ALERTA_INEXISTENTE), sim.handle(RobotCommand.AcknowledgeAlert("x"), now))
        sim.rejectNextCommand()
        assertEquals(
            CommandResponse.Rejected(RejectReason.RECHAZO_SIMULADO),
            sim.handle(RobotCommand.StartMission(bolsas, tacho), now),
        )
        assertEquals(OperationalState.INACTIVO, t().state)
    }

    @Test
    fun `alerts are acknowledged and a person nearby slows the robot down`() {
        start()
        sim.setPersonNearby(true, now)
        run(1)
        val alert = t().alerts.single { it.type == AlertType.PERSONA_CERCA }
        assertEquals(0.25, t().speedMps, 0.001)
        assertEquals(CommandResponse.Accepted, sim.handle(RobotCommand.AcknowledgeAlert(alert.id), now))
        assertTrue(t().alerts.single { it.id == alert.id }.acknowledged)
    }
}
