package com.example.robottirabasura.ui.screens.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robottirabasura.domain.repository.RobotRepository
import com.example.robottirabasura.domain.repository.SimulatorControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class SimulatorUiState(
    val connected: Boolean = false,
    val linkUp: Boolean = true,
    val binFull: Boolean = false,
    val personNearby: Boolean = false,
    val batteryTarget: Int = 50,
    val currentBattery: Int? = null,
)

sealed interface SimulatorEffect {
    data object Done : SimulatorEffect
    data object ObstacleFailed : SimulatorEffect
}

private data class Toggles(val binFull: Boolean = false, val personNearby: Boolean = false, val batteryTarget: Int = 50)

@HiltViewModel
class SimulatorViewModel @Inject constructor(
    private val controls: SimulatorControls,
    robot: RobotRepository,
) : ViewModel() {

    private val toggles = MutableStateFlow(Toggles())

    private val _effects = MutableSharedFlow<SimulatorEffect>()
    val effects: SharedFlow<SimulatorEffect> = _effects.asSharedFlow()

    val uiState: StateFlow<SimulatorUiState> =
        combine(robot.connectedRobot, controls.linkUp, robot.telemetry, toggles) { connected, linkUp, telemetry, t ->
            SimulatorUiState(
                connected = connected != null,
                linkUp = linkUp,
                binFull = t.binFull,
                personNearby = t.personNearby,
                batteryTarget = t.batteryTarget,
                currentBattery = telemetry?.batteryPercent?.roundToInt(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimulatorUiState())

    fun onLinkUp(up: Boolean) = controls.setLinkUp(up)

    fun onDropNextResponse() = done { controls.dropNextResponse() }

    fun onRejectNext() = done { controls.rejectNextCommand() }

    fun onInjectObstacle() {
        val ok = controls.injectObstacleAhead()
        viewModelScope.launch { _effects.emit(if (ok) SimulatorEffect.Done else SimulatorEffect.ObstacleFailed) }
    }

    fun onClearObstacles() = done { controls.clearObstacles() }

    fun onBinFull(full: Boolean) {
        toggles.value = toggles.value.copy(binFull = full)
        controls.setBinFull(full)
    }

    fun onPersonNearby(nearby: Boolean) {
        toggles.value = toggles.value.copy(personNearby = nearby)
        controls.setPersonNearby(nearby)
    }

    fun onBatteryTarget(percent: Int) {
        toggles.value = toggles.value.copy(batteryTarget = percent.coerceIn(0, 100))
    }

    fun onApplyBattery() = done { controls.setBattery(toggles.value.batteryTarget) }

    private fun done(action: () -> Unit) {
        action()
        viewModelScope.launch { _effects.emit(SimulatorEffect.Done) }
    }
}
