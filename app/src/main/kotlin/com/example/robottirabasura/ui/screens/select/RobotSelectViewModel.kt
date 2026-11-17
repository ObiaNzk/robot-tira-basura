package com.example.robottirabasura.ui.screens.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robottirabasura.domain.repository.RobotInfo
import com.example.robottirabasura.domain.repository.RobotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RobotSelectUiState(val robots: List<RobotInfo> = emptyList(), val connectedId: String? = null)

sealed interface RobotSelectEffect {
    data object OpenSupervision : RobotSelectEffect
}

@HiltViewModel
class RobotSelectViewModel @Inject constructor(private val robot: RobotRepository) : ViewModel() {

    val uiState: StateFlow<RobotSelectUiState> = robot.connectedRobot
        .map { RobotSelectUiState(robot.availableRobots, it?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RobotSelectUiState(robot.availableRobots))

    private val _effects = MutableSharedFlow<RobotSelectEffect>()
    val effects: SharedFlow<RobotSelectEffect> = _effects.asSharedFlow()

    fun onConnect(robotId: String) {
        robot.connect(robotId)
        viewModelScope.launch { _effects.emit(RobotSelectEffect.OpenSupervision) }
    }
}
