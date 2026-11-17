package com.example.robottirabasura.ui

import app.cash.turbine.test
import com.example.robottirabasura.domain.model.CommandStatus
import com.example.robottirabasura.domain.model.CommandType
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.OperationalState
import com.example.robottirabasura.domain.model.Position
import com.example.robottirabasura.domain.model.QrError
import com.example.robottirabasura.domain.model.RobotCommand
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.rules.DisabledReason
import com.example.robottirabasura.domain.usecase.ObserveRobotStatusUseCase
import com.example.robottirabasura.fakes.FakeClock
import com.example.robottirabasura.fakes.FakeRobotRepository
import com.example.robottirabasura.fakes.MainDispatcherRule
import com.example.robottirabasura.ui.screens.mission.CameraState
import com.example.robottirabasura.ui.screens.mission.MissionSetupEffect
import com.example.robottirabasura.ui.screens.mission.MissionSetupViewModel
import com.example.robottirabasura.ui.screens.mission.PlanPreview
import com.example.robottirabasura.ui.screens.mission.ScanFeedback
import com.example.robottirabasura.ui.screens.mission.StartStatus
import com.example.robottirabasura.ui.screens.supervision.SupervisionViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = FakeClock()
    private val robot = FakeRobotRepository()
    private val status = ObserveRobotStatusUseCase(robot, clock)

    private fun telemetry(state: OperationalState = OperationalState.INACTIVO, battery: Double = 80.0) = Telemetry(
        robotId = "g1", timestampMillis = clock.now, signalPercent = 90, batteryPercent = battery, state = state,
        position = Position(0.75, 0.75, robot.site.safePoint), headingDegrees = 90, speedMps = 0.0,
        bagsCollected = 0, payloadKg = 0.0, binFillPercent = 40,
    )

    private fun online(t: Telemetry = telemetry()) {
        robot.telemetry.value = t
        robot.lastReceivedAtMillis.value = clock.now
    }

    @Test
    fun `scanning both codes plans the route, closes the camera and enables start`() = runTest {
        online()
        val vm = MissionSetupViewModel(robot, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onOpenCamera()
        vm.onCameraPermissionResult(true)

        vm.onCodeScanned("RTB:BOLSAS:B1:1,9")
        assertEquals(DisabledReason.FALTAN_QR, vm.uiState.value.start.reason)
        assertEquals(CameraState.ESCANEANDO, vm.uiState.value.camera)

        vm.onCodeScanned("https://not.ours") // ignored
        vm.onCodeScanned("RTB:TACHO:T1:16,1")
        val state = vm.uiState.value
        assertEquals(CameraState.CERRADA, state.camera)
        assertTrue(state.preview is PlanPreview.Ready)
        assertTrue(state.route.isNotEmpty())
        assertTrue(state.start.enabled)
    }

    @Test
    fun `an invalid code is reported and keeps what was already read`() = runTest {
        online()
        val vm = MissionSetupViewModel(robot, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onCodeScanned("RTB:BOLSAS:B1:1,9")
        vm.onCodeScanned("RTB:TACHO:T1:8,1")
        assertEquals(ScanFeedback.Invalid(QrError.ON_OBSTACLE), vm.uiState.value.feedback)
        assertEquals("B1", vm.uiState.value.bolsas?.id)
        assertNull(vm.uiState.value.tacho)
    }

    @Test
    fun `start asks for confirmation, sends once and opens supervision when confirmed`() = runTest {
        online()
        val vm = MissionSetupViewModel(robot, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onCodeScanned("RTB:BOLSAS:B1:1,9")
        vm.onCodeScanned("RTB:TACHO:T1:16,1")

        vm.onStart()
        assertTrue(vm.uiState.value.confirmingStart)
        assertTrue(robot.sent.isEmpty())

        vm.effects.test {
            vm.onConfirmStart()
            assertEquals(StartStatus.Sending, vm.uiState.value.startStatus)
            assertEquals(DisabledReason.ORDEN_PENDIENTE, vm.uiState.value.start.reason)
            robot.answer(robot.commands.value.first().id, CommandStatus.CONFIRMADA)
            assertEquals(MissionSetupEffect.OpenSupervision, awaitItem())
        }
        assertEquals(1, robot.sent.size)
        assertTrue(robot.sent.single() is RobotCommand.StartMission)
        assertNull(vm.uiState.value.bolsas)
    }

    @Test
    fun `a start without answer is shown so the operator checks before retrying`() = runTest {
        online()
        val vm = MissionSetupViewModel(robot, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onCodeScanned("RTB:BOLSAS:B1:1,9")
        vm.onCodeScanned("RTB:TACHO:T1:16,1")
        vm.onStart()
        vm.onConfirmStart()
        robot.answer(robot.commands.value.first().id, CommandStatus.SIN_RESPUESTA)
        assertEquals(StartStatus.NoAnswer, vm.uiState.value.startStatus)
    }

    @Test
    fun `risky commands wait for confirmation, harmless ones go straight away`() = runTest {
        online(telemetry(OperationalState.EN_MISION))
        val vm = SupervisionViewModel(robot, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onCommand(CommandType.PAUSAR)
        assertEquals(listOf<RobotCommand>(RobotCommand.Pause), robot.sent)

        vm.onCommand(CommandType.DETENCION_OPERATIVA)
        assertEquals(CommandType.DETENCION_OPERATIVA, vm.uiState.value.confirming)
        assertEquals(1, robot.sent.size)
        vm.onConfirm()
        assertEquals(RobotCommand.OperationalStop, robot.sent.last())
        assertNull(vm.uiState.value.confirming)
    }

    @Test
    fun `old data is flagged and disables everything but the stop`() = runTest {
        online(telemetry(OperationalState.EN_MISION))
        val vm = SupervisionViewModel(robot, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        clock.now += 5_000
        testScheduler.advanceTimeBy(1_001)

        val state = vm.uiState.value
        assertEquals(LinkState.DATOS_ANTIGUOS, state.link)
        assertEquals(5L, state.ageSeconds)
        assertEquals(DisabledReason.SIN_CONEXION, state.actions[CommandType.PAUSAR]?.reason)
        assertTrue(state.actions[CommandType.DETENCION_OPERATIVA]!!.enabled)
        vm.onCommand(CommandType.PAUSAR)
        assertTrue(robot.sent.isEmpty())
    }

}
