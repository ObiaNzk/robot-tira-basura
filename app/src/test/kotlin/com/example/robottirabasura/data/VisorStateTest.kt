package com.example.robottirabasura.data

import com.example.robottirabasura.data.mirror.BagLocation
import com.example.robottirabasura.data.mirror.bagLocation
import com.example.robottirabasura.data.mirror.toVisorState
import com.example.robottirabasura.data.sim.RobotSimulator
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.domain.model.StepKind
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.fakes.sequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisorStateTest {
    private val map = SiteMap.DEMO
    private val sim = RobotSimulator(map, "g1", sequentialIds(), initialBattery = 90.0)
    private var now = 0L

    private fun until(condition: (Telemetry) -> Boolean): Telemetry {
        repeat(2_000) {
            now += 200
            sim.tick(200, now)
            sim.snapshot(now, 90).let { if (condition(it)) return it }
        }
        throw AssertionError("not reached")
    }

    private fun start() = sim.handle(
        RobotCommand.StartMission(QrTarget(QrKind.BOLSAS, "B1", Cell(1, 9)), QrTarget(QrKind.TACHO, "T1", Cell(16, 1))), now,
    )

    @Test
    fun `layout round-trips so the visor can check it draws the same tables`() {
        assertEquals(SiteMap.DEMO.toLayout(), SiteMap.fromLayout("x", SiteMap.DEMO.toLayout(), 0.5).toLayout())
        assertEquals("..................", SiteMap.DEMO.toLayout().first())
        assertEquals('S', SiteMap.DEMO.toLayout()[1][1])
    }

    @Test
    fun `the bag goes from the floor to the hand and into the bin`() {
        assertNull(sim.snapshot(now, 90).bagLocation())
        start()
        assertEquals(BagLocation.EN_PISO, sim.snapshot(now, 90).bagLocation())
        assertEquals(BagLocation.EN_MANO, until { it.mission?.currentKind == StepKind.NAVEGAR_A_TACHO }.bagLocation())
        assertEquals(BagLocation.EN_TACHO, until { it.mission?.result != null }.bagLocation())
    }

    @Test
    fun `a bag left beside a full bin is drawn there`() {
        sim.setBinFull(true)
        start()
        until { it.waitReason != null }
        sim.handle(RobotCommand.LeaveBesideBin, now)
        assertEquals(BagLocation.JUNTO_AL_TACHO, sim.snapshot(now, 90).bagLocation())
    }

    @Test
    fun `visor state carries step, targets in meters and obstacles`() {
        start()
        sim.injectObstacleAhead()
        val state = until { it.speedMps > 0 }.toVisorState(map)
        assertEquals("NAVEGAR_A_BOLSAS", state.step)
        assertEquals(listOf(0.75, 4.75), state.bags)
        assertEquals(listOf(8.25, 0.75), state.bin)
        assertEquals(1, state.obstacles.size)
        assertEquals("EN_PISO", state.bag)
    }
}
