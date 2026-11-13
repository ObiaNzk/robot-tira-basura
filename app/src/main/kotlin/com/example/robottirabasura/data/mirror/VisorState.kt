package com.example.robottirabasura.data.mirror

import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.MissionResult
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepKind
import com.example.robottirabasura.domain.model.StepStatus
import com.example.robottirabasura.domain.model.Telemetry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the optional MuJoCo visor needs to draw the robot (see mujoco/README.md). Not a Unitree protocol. */
@Serializable
data class VisorState(
    @SerialName("x_m") val xMeters: Double,
    @SerialName("y_m") val yMeters: Double,
    @SerialName("heading_deg") val headingDegrees: Int,
    @SerialName("speed_mps") val speedMps: Double,
    val state: String,
    @SerialName("wait_reason") val waitReason: String?,
    val step: String?,
    val bag: String?,
    val bags: List<Double>?,
    val bin: List<Double>?,
    val obstacles: List<List<Int>>,
    val layout: List<String>,
)

enum class BagLocation { EN_PISO, EN_MANO, EN_TACHO, JUNTO_AL_TACHO }

fun Telemetry.toVisorState(site: SiteMap): VisorState {
    val mission = mission
    fun center(cell: Cell) = listOf((cell.x + 0.5) * site.cellSizeMeters, (cell.y + 0.5) * site.cellSizeMeters)
    return VisorState(
        xMeters = position.xMeters,
        yMeters = position.yMeters,
        headingDegrees = headingDegrees,
        speedMps = speedMps,
        state = state.name,
        waitReason = waitReason?.name,
        step = mission?.takeIf { isOnMission }?.currentKind?.name,
        bag = bagLocation()?.name,
        bags = mission?.plan?.bolsas?.cell?.let(::center),
        bin = mission?.plan?.tacho?.cell?.let(::center),
        obstacles = extraObstacles.map { listOf(it.x, it.y) },
        layout = site.toLayout(),
    )
}

/** Where the bag of the current (or last) mission is, derived from the macro's step statuses. */
fun Telemetry.bagLocation(): BagLocation? {
    val mission = mission ?: return null
    if (payloadKg > 0) return BagLocation.EN_MANO
    val statuses = mission.stepStatuses
    fun status(kind: StepKind) = statuses.getOrNull(mission.plan.steps.indexOfFirst { it.kind == kind })
    return when {
        status(StepKind.DEPOSITAR_BOLSA) == StepStatus.COMPLETADO -> BagLocation.EN_TACHO
        mission.result == MissionResult.BOLSA_JUNTO_AL_TACHO -> BagLocation.JUNTO_AL_TACHO
        status(StepKind.RECOGER_BOLSA) == StepStatus.COMPLETADO -> BagLocation.JUNTO_AL_TACHO
        else -> BagLocation.EN_PISO
    }
}
