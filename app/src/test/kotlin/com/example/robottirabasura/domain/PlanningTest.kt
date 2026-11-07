package com.example.robottirabasura.domain

import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.QrCodes
import com.example.robottirabasura.domain.model.QrError
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrParseResult
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepKind
import com.example.robottirabasura.domain.planning.MissionPlanner
import com.example.robottirabasura.domain.planning.PathPlanner
import com.example.robottirabasura.domain.planning.PlanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlanningTest {
    private val open = SiteMap.fromLayout("t", listOf("S....", ".....", "....."), 0.5)
    private val wall = SiteMap.fromLayout("t", listOf("S.#..", "..#..", "....."), 0.5)

    @Test
    fun `straight route on an empty grid is the Manhattan distance`() {
        val path = PathPlanner.findPath(open, Cell(0, 0), Cell(4, 2))!!
        assertEquals(Cell(0, 0), path.first())
        assertEquals(Cell(4, 2), path.last())
        assertEquals(6 + 1, path.size)
    }

    @Test
    fun `route goes around a wall and every step moves one cell to a free cell`() {
        val path = PathPlanner.findPath(wall, Cell(0, 0), Cell(4, 0))!!
        assertEquals(9, path.size) // down to row 2, across, back up
        path.zipWithNext().forEach { (a, b) -> assertEquals(1, abs(a.x - b.x) + abs(a.y - b.y)) }
        assertTrue(path.all { wall.isFree(it) })
    }

    @Test
    fun `no route when the goal is walled in or blocked`() {
        val closed = SiteMap.fromLayout("t", listOf("S.#..", "..#..", "..#.."), 0.5)
        assertNull(PathPlanner.findPath(closed, Cell(0, 0), Cell(4, 0)))
        assertNull(PathPlanner.findPath(open, Cell(0, 0), Cell(4, 0), extraBlocked = setOf(Cell(4, 0))))
    }

    @Test
    fun `extra obstacles change the route`() {
        val direct = PathPlanner.findPath(open, Cell(0, 0), Cell(4, 0))!!
        val around = PathPlanner.findPath(open, Cell(0, 0), Cell(4, 0), extraBlocked = setOf(Cell(2, 0)))!!
        assertTrue(Cell(2, 0) in direct)
        assertTrue(Cell(2, 0) !in around)
    }

    @Test
    fun `qr codes are parsed, rejected or ignored`() {
        val map = SiteMap.DEMO
        val ok = QrCodes.parse("RTB:BOLSAS:B1:1,9", map) as QrParseResult.Valid
        assertEquals(QrTarget(QrKind.BOLSAS, "B1", Cell(1, 9)), ok.target)
        assertEquals(QrParseResult.NotOurs, QrCodes.parse("https://menu.example/qr", map))
        assertEquals(QrParseResult.Invalid(QrError.MALFORMED), QrCodes.parse("RTB:BASURA:X:1,1", map))
        assertEquals(QrParseResult.Invalid(QrError.OUT_OF_MAP), QrCodes.parse("RTB:TACHO:T9:99,1", map))
        assertEquals(QrParseResult.Invalid(QrError.ON_OBSTACLE), QrCodes.parse("RTB:TACHO:T9:8,1", map))
        assertEquals("RTB:TACHO:T1:16,1", QrCodes.build(QrTarget(QrKind.TACHO, "T1", Cell(16, 1))))
    }

    @Test
    fun `every demo code is valid on the demo map`() {
        QrCodes.DEMO_CODES.forEach { assertTrue(it, QrCodes.parse(it, SiteMap.DEMO) is QrParseResult.Valid) }
    }

    @Test
    fun `mission plan has the seven macro steps with connected routes`() {
        val map = SiteMap.DEMO
        val bolsas = QrTarget(QrKind.BOLSAS, "B1", Cell(1, 9))
        val tacho = QrTarget(QrKind.TACHO, "T1", Cell(16, 1))
        val plan = (MissionPlanner.plan(map, map.safePoint, bolsas, tacho) as PlanResult.Ready).plan
        assertEquals(StepKind.entries.toList(), plan.steps.map { it.kind })
        assertEquals(bolsas.cell, plan.steps[0].path.last())
        assertEquals(bolsas.cell, plan.steps[2].path.first())
        assertEquals(tacho.cell, plan.steps[2].path.last())
        assertEquals(map.safePoint, plan.steps[6].path.last())
        assertTrue(plan.distanceMeters(map) > 0)
    }

    @Test
    fun `mission plan rejects swapped codes and unreachable targets`() {
        val map = SiteMap.DEMO
        val bolsas = QrTarget(QrKind.BOLSAS, "B1", Cell(1, 9))
        val tacho = QrTarget(QrKind.TACHO, "T1", Cell(16, 1))
        assertEquals(PlanResult.WrongTargets, MissionPlanner.plan(map, map.safePoint, tacho, bolsas))
        val walledBags = setOf(Cell(0, 9), Cell(1, 8), Cell(2, 9), Cell(1, 10))
        assertEquals(
            PlanResult.Unreachable(StepKind.NAVEGAR_A_BOLSAS),
            MissionPlanner.plan(map, map.safePoint, bolsas, tacho, walledBags),
        )
    }
}
