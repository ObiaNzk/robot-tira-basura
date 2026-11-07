package com.example.robottirabasura.domain.model

/** Steps of the "recoger y tirar la basura" macro, in execution order. */
enum class StepKind(val isNavigation: Boolean) {
    NAVEGAR_A_BOLSAS(true),
    RECOGER_BOLSA(false),
    NAVEGAR_A_TACHO(true),
    LEVANTAR_TAPA(false),
    DEPOSITAR_BOLSA(false),
    BAJAR_TAPA(false),
    REGRESAR_A_BASE(true),
}

/**
 * One step of the plan. Navigation steps carry the route (first cell = where the step starts); manipulation steps
 * carry how long the simulated motion takes.
 */
data class MacroStep(
    val kind: StepKind,
    val path: List<Cell> = emptyList(),
    val durationMillis: Long = 0,
)

data class MissionPlan(
    val bolsas: QrTarget,
    val tacho: QrTarget,
    val steps: List<MacroStep>,
) {
    val routeCells: Int get() = steps.sumOf { (it.path.size - 1).coerceAtLeast(0) }
    fun distanceMeters(map: SiteMap): Double = routeCells * map.cellSizeMeters
}

enum class StepStatus { PENDIENTE, EN_CURSO, COMPLETADO, OMITIDO }

enum class MissionResult { COMPLETADA, CANCELADA, ABORTADA_REGRESO, BOLSA_JUNTO_AL_TACHO }

data class MissionProgress(
    val missionId: String,
    val plan: MissionPlan,
    val stepStatuses: List<StepStatus>,
    val currentStep: Int,
    val progressPercent: Int,
    val result: MissionResult? = null,
) {
    val currentKind: StepKind? get() = plan.steps.getOrNull(currentStep)?.kind
}
