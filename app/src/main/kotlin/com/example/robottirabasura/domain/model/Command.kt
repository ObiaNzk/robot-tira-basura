package com.example.robottirabasura.domain.model

enum class CommandType {
    INICIAR_MISION, PAUSAR, REANUDAR, CANCELAR, REGRESAR_A_PUNTO_SEGURO, DETENCION_OPERATIVA, RECONOCER_ALERTA,
    REPLANIFICAR, DEJAR_JUNTO_AL_TACHO,
}

sealed interface RobotCommand {
    val type: CommandType

    data class StartMission(val bolsas: QrTarget, val tacho: QrTarget) : RobotCommand {
        override val type = CommandType.INICIAR_MISION
    }
    data object Pause : RobotCommand { override val type = CommandType.PAUSAR }
    data object Resume : RobotCommand { override val type = CommandType.REANUDAR }
    data object Cancel : RobotCommand { override val type = CommandType.CANCELAR }
    data object ReturnToSafePoint : RobotCommand { override val type = CommandType.REGRESAR_A_PUNTO_SEGURO }
    data object OperationalStop : RobotCommand { override val type = CommandType.DETENCION_OPERATIVA }
    data class AcknowledgeAlert(val alertId: String) : RobotCommand { override val type = CommandType.RECONOCER_ALERTA }
    data object Replan : RobotCommand { override val type = CommandType.REPLANIFICAR }
    data object LeaveBesideBin : RobotCommand { override val type = CommandType.DEJAR_JUNTO_AL_TACHO }
}

/** Sent is not executed: an order is only confirmed when the robot answers. */
enum class CommandStatus { PENDIENTE, CONFIRMADA, RECHAZADA, SIN_RESPUESTA }

enum class RejectReason {
    ESTADO_INVALIDO, BATERIA_INSUFICIENTE, SIN_RUTA, ALERTA_CRITICA_PENDIENTE, ALERTA_INEXISTENTE, RECHAZO_SIMULADO,
}

data class CommandRecord(
    val id: String,
    val command: RobotCommand,
    val sentAtMillis: Long,
    val status: CommandStatus,
    val answeredAtMillis: Long? = null,
    val rejectReason: RejectReason? = null,
)

/** The robot's answer to a command. */
sealed interface CommandResponse {
    data object Accepted : CommandResponse
    data class Rejected(val reason: RejectReason) : CommandResponse
}
