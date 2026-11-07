package com.example.robottirabasura.domain.model

enum class EventType {
    MISION_INICIADA, PASO_COMPLETADO, MISION_FINALIZADA, ALERTA, ALERTA_RECONOCIDA, COMANDO, CONEXION,
}

/** Something worth keeping in the history. `detail` is a short human-readable summary. */
data class RobotEvent(
    val timestampMillis: Long,
    val type: EventType,
    val severity: AlertSeverity,
    val detail: String,
    val missionId: String? = null,
)

data class MissionRecord(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val result: MissionResult?,
    val bolsasId: String,
    val tachoId: String,
    val distanceMeters: Double,
)
