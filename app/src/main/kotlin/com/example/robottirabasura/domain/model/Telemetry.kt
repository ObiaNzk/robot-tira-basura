package com.example.robottirabasura.domain.model

enum class OperationalState { INACTIVO, CARGANDO, EN_MISION, PAUSADO, DETENIDO, ESPERANDO_OPERADOR, REGRESANDO }

/** Why the robot stopped and asks the operator to decide. */
enum class WaitReason { OBSTACULO, TACHO_LLENO }

enum class AlertSeverity { INFO, ADVERTENCIA, CRITICA }

enum class AlertType { BATERIA_BAJA, BATERIA_CRITICA, OBSTACULO, TACHO_LLENO, PERSONA_CERCA }

data class Alert(
    val id: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val createdAtMillis: Long,
    val acknowledged: Boolean = false,
)

data class Position(val xMeters: Double, val yMeters: Double, val cell: Cell)

/**
 * What the robot reports. `timestampMillis` is when the robot produced the sample; the app decides how fresh it is
 * from when it *received* it (see [LinkState]).
 */
data class Telemetry(
    val robotId: String,
    val timestampMillis: Long,
    val signalPercent: Int,
    val batteryPercent: Double,
    val state: OperationalState,
    val waitReason: WaitReason? = null,
    val mission: MissionProgress? = null,
    val position: Position,
    val headingDegrees: Int,
    val speedMps: Double,
    val alerts: List<Alert> = emptyList(),
    val extraObstacles: Set<Cell> = emptySet(),
    /** Cells still ahead on the route being followed (mission leg or return), starting at the current cell. */
    val activeRoute: List<Cell> = emptyList(),
    // Service variables.
    val bagsCollected: Int,
    val payloadKg: Double,
    val binFillPercent: Int,
) {
    val unacknowledgedAlerts: List<Alert> get() = alerts.filterNot { it.acknowledged }
    val isOnMission: Boolean get() = mission != null && mission.result == null
}

/** Freshness of the data as seen by the app. */
enum class LinkState { CONECTADO, DATOS_ANTIGUOS, DESCONECTADO }

object Freshness {
    const val STALE_AFTER_MILLIS = 3_000L
    const val LOST_AFTER_MILLIS = 10_000L

    fun linkState(lastReceivedAtMillis: Long?, nowMillis: Long): LinkState {
        if (lastReceivedAtMillis == null) return LinkState.DESCONECTADO
        val age = nowMillis - lastReceivedAtMillis
        return when {
            age <= STALE_AFTER_MILLIS -> LinkState.CONECTADO
            age <= LOST_AFTER_MILLIS -> LinkState.DATOS_ANTIGUOS
            else -> LinkState.DESCONECTADO
        }
    }
}
