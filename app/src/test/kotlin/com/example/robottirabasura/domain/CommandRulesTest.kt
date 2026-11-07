package com.example.robottirabasura.domain

import com.example.robottirabasura.domain.model.Alert
import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.AlertType
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.Freshness
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.Position
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.model.WaitReason
import com.example.robottirabasura.domain.rules.CommandContext
import com.example.robottirabasura.domain.rules.CommandRules
import com.example.robottirabasura.domain.rules.DisabledReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRulesTest {
    private val safe = Cell(1, 1)
    private val online = CommandContext(LinkState.CONECTADO, safe, planReady = true)

    private fun telemetry(
        state: OperationalState = OperationalState.INACTIVO,
        battery: Double = 80.0,
        cell: Cell = safe,
        wait: WaitReason? = null,
        alerts: List<Alert> = emptyList(),
    ) = Telemetry(
        robotId = "g1", timestampMillis = 0, signalPercent = 90, batteryPercent = battery, state = state,
        waitReason = wait, position = Position(0.0, 0.0, cell), headingDegrees = 0, speedMps = 0.0, alerts = alerts,
        bagsCollected = 0, payloadKg = 0.0, binFillPercent = 0,
    )

    private fun reason(type: CommandType, t: Telemetry?, ctx: CommandContext = online) =
        CommandRules.availability(type, t, ctx).reason

    @Test
    fun `start needs idle robot, both QR, battery and no open critical alert`() {
        assertTrue(CommandRules.availability(CommandType.INICIAR_MISION, telemetry(), online).enabled)
        assertEquals(DisabledReason.FALTAN_QR, reason(CommandType.INICIAR_MISION, telemetry(), online.copy(planReady = false)))
        assertEquals(DisabledReason.BATERIA_BAJA, reason(CommandType.INICIAR_MISION, telemetry(battery = 24.9)))
        assertEquals(DisabledReason.ESTADO_NO_PERMITE, reason(CommandType.INICIAR_MISION, telemetry(OperationalState.EN_MISION)))
        val critical = Alert("a", AlertType.BATERIA_CRITICA, AlertSeverity.CRITICA, 0)
        assertEquals(DisabledReason.ALERTA_CRITICA, reason(CommandType.INICIAR_MISION, telemetry(alerts = listOf(critical))))
    }

    @Test
    fun `without a fresh link only the stop can be tried`() {
        val stale = online.copy(link = LinkState.DATOS_ANTIGUOS)
        val moving = telemetry(OperationalState.EN_MISION)
        assertEquals(DisabledReason.SIN_CONEXION, reason(CommandType.PAUSAR, moving, stale))
        assertTrue(CommandRules.availability(CommandType.DETENCION_OPERATIVA, moving, stale).enabled)
        assertEquals(DisabledReason.SIN_DATOS, reason(CommandType.PAUSAR, null))
    }

    @Test
    fun `pause, resume and cancel follow the operational state`() {
        assertTrue(CommandRules.availability(CommandType.PAUSAR, telemetry(OperationalState.REGRESANDO), online).enabled)
        assertFalse(CommandRules.availability(CommandType.PAUSAR, telemetry(OperationalState.PAUSADO), online).enabled)
        assertTrue(CommandRules.availability(CommandType.REANUDAR, telemetry(OperationalState.DETENIDO), online).enabled)
        assertTrue(
            CommandRules.availability(CommandType.REANUDAR, telemetry(OperationalState.ESPERANDO_OPERADOR, wait = WaitReason.TACHO_LLENO), online).enabled
        )
        assertFalse(
            CommandRules.availability(CommandType.REANUDAR, telemetry(OperationalState.ESPERANDO_OPERADOR, wait = WaitReason.OBSTACULO), online).enabled
        )
        // Cancel needs an active mission, not just the state.
        assertFalse(CommandRules.availability(CommandType.CANCELAR, telemetry(OperationalState.PAUSADO), online).enabled)
    }

    @Test
    fun `return is not offered when already resting at the safe point`() {
        assertEquals(DisabledReason.YA_EN_PUNTO_SEGURO, reason(CommandType.REGRESAR_A_PUNTO_SEGURO, telemetry()))
        assertTrue(CommandRules.availability(CommandType.REGRESAR_A_PUNTO_SEGURO, telemetry(cell = Cell(5, 5)), online).enabled)
        assertFalse(CommandRules.availability(CommandType.REGRESAR_A_PUNTO_SEGURO, telemetry(OperationalState.REGRESANDO), online).enabled)
    }

    @Test
    fun `operator decisions match the wait reason`() {
        val obstacle = telemetry(OperationalState.ESPERANDO_OPERADOR, wait = WaitReason.OBSTACULO)
        val full = telemetry(OperationalState.ESPERANDO_OPERADOR, wait = WaitReason.TACHO_LLENO)
        assertTrue(CommandRules.availability(CommandType.REPLANIFICAR, obstacle, online).enabled)
        assertFalse(CommandRules.availability(CommandType.REPLANIFICAR, full, online).enabled)
        assertTrue(CommandRules.availability(CommandType.DEJAR_JUNTO_AL_TACHO, full, online).enabled)
        assertFalse(CommandRules.availability(CommandType.DEJAR_JUNTO_AL_TACHO, obstacle, online).enabled)
    }

    @Test
    fun `a command waiting for an answer can't be sent twice`() {
        val ctx = online.copy(pending = setOf(CommandType.PAUSAR))
        assertEquals(DisabledReason.ORDEN_PENDIENTE, reason(CommandType.PAUSAR, telemetry(OperationalState.EN_MISION), ctx))
    }

    @Test
    fun `freshness turns into stale and then lost`() {
        assertEquals(LinkState.DESCONECTADO, Freshness.linkState(null, 0))
        assertEquals(LinkState.CONECTADO, Freshness.linkState(0, Freshness.STALE_AFTER_MILLIS))
        assertEquals(LinkState.DATOS_ANTIGUOS, Freshness.linkState(0, Freshness.STALE_AFTER_MILLIS + 1))
        assertEquals(LinkState.DESCONECTADO, Freshness.linkState(0, Freshness.LOST_AFTER_MILLIS + 1))
    }
}
