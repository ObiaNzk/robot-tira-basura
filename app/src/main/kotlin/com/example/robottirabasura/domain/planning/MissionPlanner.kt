package com.example.robottirabasura.domain.planning

import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.MacroStep
import com.example.robottirabasura.domain.model.MissionPlan
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepKind

sealed interface PlanResult {
    data class Ready(val plan: MissionPlan) : PlanResult
    data class Unreachable(val leg: StepKind) : PlanResult
    data object WrongTargets : PlanResult
}

/**
 * Builds the "recoger y tirar" macro: go to the bags, pick one up, go to the bin, lift the lid, drop the bag, lower the
 * lid and go back to the safe point. Manipulation durations are simulated values, not measured G1 timings.
 */
object MissionPlanner {
    /** Walking speed used for estimates (the simulator walks one 0.5 m cell per second). */
    const val NOMINAL_SPEED_MPS = 0.5
    const val PICK_UP_MILLIS = 3_000L
    const val LIFT_LID_MILLIS = 2_500L
    const val DROP_BAG_MILLIS = 2_500L
    const val LOWER_LID_MILLIS = 2_000L

    fun plan(map: SiteMap, start: Cell, bolsas: QrTarget, tacho: QrTarget, extraBlocked: Set<Cell> = emptySet()): PlanResult {
        if (bolsas.kind != QrKind.BOLSAS || tacho.kind != QrKind.TACHO) return PlanResult.WrongTargets
        val toBags = PathPlanner.findPath(map, start, bolsas.cell, extraBlocked)
            ?: return PlanResult.Unreachable(StepKind.NAVEGAR_A_BOLSAS)
        val toBin = PathPlanner.findPath(map, bolsas.cell, tacho.cell, extraBlocked)
            ?: return PlanResult.Unreachable(StepKind.NAVEGAR_A_TACHO)
        val toBase = PathPlanner.findPath(map, tacho.cell, map.safePoint, extraBlocked)
            ?: return PlanResult.Unreachable(StepKind.REGRESAR_A_BASE)
        return PlanResult.Ready(
            MissionPlan(
                bolsas = bolsas,
                tacho = tacho,
                steps = listOf(
                    MacroStep(StepKind.NAVEGAR_A_BOLSAS, path = toBags),
                    MacroStep(StepKind.RECOGER_BOLSA, durationMillis = PICK_UP_MILLIS),
                    MacroStep(StepKind.NAVEGAR_A_TACHO, path = toBin),
                    MacroStep(StepKind.LEVANTAR_TAPA, durationMillis = LIFT_LID_MILLIS),
                    MacroStep(StepKind.DEPOSITAR_BOLSA, durationMillis = DROP_BAG_MILLIS),
                    MacroStep(StepKind.BAJAR_TAPA, durationMillis = LOWER_LID_MILLIS),
                    MacroStep(StepKind.REGRESAR_A_BASE, path = toBase),
                ),
            )
        )
    }
}
