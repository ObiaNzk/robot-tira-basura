package com.example.robottirabasura.ui.components

import androidx.annotation.StringRes
import com.example.robottirabasura.R
import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.AlertType
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.MissionResult
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.QrError
import com.example.robottirabasura.domain.model.RejectReason
import com.example.robottirabasura.domain.model.StepKind
import com.example.robottirabasura.domain.rules.DisabledReason

/** Visual meaning of a status: maps to the fixed status colors. */
enum class Tone { OK, WARNING, ERROR, NEUTRAL }

@get:StringRes
val OperationalState.label: Int
    get() = when (this) {
        OperationalState.INACTIVO -> R.string.state_idle
        OperationalState.CARGANDO -> R.string.state_charging
        OperationalState.EN_MISION -> R.string.state_mission
        OperationalState.PAUSADO -> R.string.state_paused
        OperationalState.DETENIDO -> R.string.state_stopped
        OperationalState.ESPERANDO_OPERADOR -> R.string.state_waiting
        OperationalState.REGRESANDO -> R.string.state_returning
    }

val OperationalState.tone: Tone
    get() = when (this) {
        OperationalState.EN_MISION, OperationalState.REGRESANDO -> Tone.OK
        OperationalState.PAUSADO, OperationalState.ESPERANDO_OPERADOR -> Tone.WARNING
        OperationalState.DETENIDO -> Tone.ERROR
        OperationalState.INACTIVO, OperationalState.CARGANDO -> Tone.NEUTRAL
    }

@get:StringRes
val StepKind.label: Int
    get() = when (this) {
        StepKind.NAVEGAR_A_BOLSAS -> R.string.step_to_bags
        StepKind.RECOGER_BOLSA -> R.string.step_pick
        StepKind.NAVEGAR_A_TACHO -> R.string.step_to_bin
        StepKind.LEVANTAR_TAPA -> R.string.step_lift_lid
        StepKind.DEPOSITAR_BOLSA -> R.string.step_drop
        StepKind.BAJAR_TAPA -> R.string.step_lower_lid
        StepKind.REGRESAR_A_BASE -> R.string.step_return
    }

@StringRes
fun MissionResult?.label(): Int = when (this) {
    null -> R.string.result_running
    MissionResult.COMPLETADA -> R.string.result_done
    MissionResult.CANCELADA -> R.string.result_cancelled
    MissionResult.ABORTADA_REGRESO -> R.string.result_aborted
    MissionResult.BOLSA_JUNTO_AL_TACHO -> R.string.result_beside_bin
}

fun MissionResult?.tone(): Tone = when (this) {
    null -> Tone.NEUTRAL
    MissionResult.COMPLETADA -> Tone.OK
    MissionResult.BOLSA_JUNTO_AL_TACHO, MissionResult.ABORTADA_REGRESO -> Tone.WARNING
    MissionResult.CANCELADA -> Tone.ERROR
}

@get:StringRes
val CommandType.label: Int
    get() = when (this) {
        CommandType.INICIAR_MISION -> R.string.cmd_start
        CommandType.PAUSAR -> R.string.cmd_pause
        CommandType.REANUDAR -> R.string.cmd_resume
        CommandType.CANCELAR -> R.string.cmd_cancel
        CommandType.REGRESAR_A_PUNTO_SEGURO -> R.string.cmd_return
        CommandType.DETENCION_OPERATIVA -> R.string.cmd_stop
        CommandType.RECONOCER_ALERTA -> R.string.cmd_ack
        CommandType.REPLANIFICAR -> R.string.cmd_replan
        CommandType.DEJAR_JUNTO_AL_TACHO -> R.string.cmd_leave
    }

@get:StringRes
val CommandStatus.label: Int
    get() = when (this) {
        CommandStatus.PENDIENTE -> R.string.status_pending
        CommandStatus.CONFIRMADA -> R.string.status_confirmed
        CommandStatus.RECHAZADA -> R.string.status_rejected
        CommandStatus.SIN_RESPUESTA -> R.string.status_no_answer
    }

val CommandStatus.tone: Tone
    get() = when (this) {
        CommandStatus.PENDIENTE -> Tone.NEUTRAL
        CommandStatus.CONFIRMADA -> Tone.OK
        CommandStatus.RECHAZADA -> Tone.ERROR
        CommandStatus.SIN_RESPUESTA -> Tone.WARNING
    }

@get:StringRes
val DisabledReason.label: Int
    get() = when (this) {
        DisabledReason.SIN_CONEXION -> R.string.disabled_no_link
        DisabledReason.SIN_DATOS -> R.string.disabled_no_data
        DisabledReason.ESTADO_NO_PERMITE -> R.string.disabled_state
        DisabledReason.FALTAN_QR -> R.string.disabled_qr
        DisabledReason.BATERIA_BAJA -> R.string.disabled_battery
        DisabledReason.ALERTA_CRITICA -> R.string.disabled_alert
        DisabledReason.YA_EN_PUNTO_SEGURO -> R.string.disabled_at_base
        DisabledReason.ORDEN_PENDIENTE -> R.string.disabled_pending
    }

@get:StringRes
val RejectReason.label: Int
    get() = when (this) {
        RejectReason.ESTADO_INVALIDO -> R.string.reject_state
        RejectReason.BATERIA_INSUFICIENTE -> R.string.reject_battery
        RejectReason.SIN_RUTA -> R.string.reject_no_route
        RejectReason.ALERTA_CRITICA_PENDIENTE -> R.string.reject_alert
        RejectReason.ALERTA_INEXISTENTE -> R.string.reject_no_alert
        RejectReason.RECHAZO_SIMULADO -> R.string.reject_simulated
    }

@get:StringRes
val AlertType.label: Int
    get() = when (this) {
        AlertType.BATERIA_BAJA -> R.string.alert_low_battery
        AlertType.BATERIA_CRITICA -> R.string.alert_critical_battery
        AlertType.OBSTACULO -> R.string.alert_obstacle
        AlertType.TACHO_LLENO -> R.string.alert_bin_full
        AlertType.PERSONA_CERCA -> R.string.alert_person
    }

@get:StringRes
val AlertSeverity.label: Int
    get() = when (this) {
        AlertSeverity.INFO -> R.string.alert_info
        AlertSeverity.ADVERTENCIA -> R.string.alert_warning
        AlertSeverity.CRITICA -> R.string.alert_critical
    }

val AlertSeverity.tone: Tone
    get() = when (this) {
        AlertSeverity.INFO -> Tone.NEUTRAL
        AlertSeverity.ADVERTENCIA -> Tone.WARNING
        AlertSeverity.CRITICA -> Tone.ERROR
    }

@get:StringRes
val LinkState.label: Int
    get() = when (this) {
        LinkState.CONECTADO -> R.string.link_connected
        LinkState.DATOS_ANTIGUOS -> R.string.link_stale
        LinkState.DESCONECTADO -> R.string.link_lost
    }

val LinkState.tone: Tone
    get() = when (this) {
        LinkState.CONECTADO -> Tone.OK
        LinkState.DATOS_ANTIGUOS -> Tone.WARNING
        LinkState.DESCONECTADO -> Tone.ERROR
    }

@get:StringRes
val QrError.label: Int
    get() = when (this) {
        QrError.MALFORMED -> R.string.mission_invalid_format
        QrError.OUT_OF_MAP -> R.string.mission_invalid_out
        QrError.ON_OBSTACLE -> R.string.mission_invalid_obstacle
    }
