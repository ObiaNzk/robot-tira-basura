package com.example.robottirabasura.domain.rules

import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.OperationalState.CARGANDO
import com.example.robottirabasura.domain.model.OperationalState.DETENIDO
import com.example.robottirabasura.domain.model.OperationalState.EN_MISION
import com.example.robottirabasura.domain.model.OperationalState.ESPERANDO_OPERADOR
import com.example.robottirabasura.domain.model.OperationalState.INACTIVO
import com.example.robottirabasura.domain.model.OperationalState.PAUSADO
import com.example.robottirabasura.domain.model.OperationalState.REGRESANDO
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.model.WaitReason

/** Why a command can't be sent right now. Shown under the disabled button. */
enum class DisabledReason {
    SIN_CONEXION, SIN_DATOS, ESTADO_NO_PERMITE, FALTAN_QR, BATERIA_BAJA, ALERTA_CRITICA, YA_EN_PUNTO_SEGURO,
    ORDEN_PENDIENTE,
}

data class Availability(val enabled: Boolean, val reason: DisabledReason? = null) {
    companion object {
        val ENABLED = Availability(true)
        fun disabled(reason: DisabledReason) = Availability(false, reason)
    }
}

/** What the rules need to know besides the telemetry. */
data class CommandContext(
    val link: LinkState,
    val safePoint: Cell,
    val planReady: Boolean = false,
    val pending: Set<CommandType> = emptySet(),
)

/**
 * Enabling conditions for every operator command, evaluated in the app before sending. The robot validates again
 * (the app may be looking at old data), so a command enabled here can still be rejected.
 */
object CommandRules {
    const val MIN_BATTERY_TO_START = 25.0

    private val MOVING_OR_HELD = setOf(EN_MISION, REGRESANDO, PAUSADO, ESPERANDO_OPERADOR)
    private val MISSION_STATES = setOf(EN_MISION, PAUSADO, DETENIDO, ESPERANDO_OPERADOR)

    fun availability(type: CommandType, telemetry: Telemetry?, context: CommandContext): Availability {
        if (telemetry == null) return disabled(DisabledReason.SIN_DATOS)
        val state = telemetry.state
        // The stop is offered whenever the robot may move, even without a link: the operator must be able to try it,
        // and the UI shows "sin respuesta" if it doesn't arrive. It never replaces a physical emergency stop.
        if (type == CommandType.DETENCION_OPERATIVA) return allowIf(state in MOVING_OR_HELD)
        if (context.link != LinkState.CONECTADO) return disabled(DisabledReason.SIN_CONEXION)
        if (type in context.pending) return disabled(DisabledReason.ORDEN_PENDIENTE)
        return when (type) {
            CommandType.INICIAR_MISION -> when {
                state != INACTIVO && state != CARGANDO -> disabled(DisabledReason.ESTADO_NO_PERMITE)
                !context.planReady -> disabled(DisabledReason.FALTAN_QR)
                telemetry.batteryPercent < MIN_BATTERY_TO_START -> disabled(DisabledReason.BATERIA_BAJA)
                telemetry.unacknowledgedAlerts.any { it.severity == AlertSeverity.CRITICA } ->
                    disabled(DisabledReason.ALERTA_CRITICA)
                else -> Availability.ENABLED
            }
            CommandType.PAUSAR -> allowIf(state == EN_MISION || state == REGRESANDO)
            CommandType.REANUDAR -> allowIf(
                state == PAUSADO || state == DETENIDO ||
                    (state == ESPERANDO_OPERADOR && telemetry.waitReason == WaitReason.TACHO_LLENO)
            )
            CommandType.CANCELAR -> allowIf(state in MISSION_STATES && telemetry.isOnMission)
            CommandType.REGRESAR_A_PUNTO_SEGURO -> when {
                state == REGRESANDO -> disabled(DisabledReason.ESTADO_NO_PERMITE)
                (state == INACTIVO || state == CARGANDO) && telemetry.position.cell == context.safePoint ->
                    disabled(DisabledReason.YA_EN_PUNTO_SEGURO)
                else -> Availability.ENABLED
            }
            CommandType.RECONOCER_ALERTA -> allowIf(telemetry.unacknowledgedAlerts.isNotEmpty())
            CommandType.REPLANIFICAR -> allowIf(state == ESPERANDO_OPERADOR && telemetry.waitReason == WaitReason.OBSTACULO)
            CommandType.DEJAR_JUNTO_AL_TACHO ->
                allowIf(state == ESPERANDO_OPERADOR && telemetry.waitReason == WaitReason.TACHO_LLENO)
            CommandType.DETENCION_OPERATIVA -> error("handled above")
        }
    }

    private fun disabled(reason: DisabledReason) = Availability.disabled(reason)

    private fun allowIf(condition: Boolean) =
        if (condition) Availability.ENABLED else disabled(DisabledReason.ESTADO_NO_PERMITE)
}
