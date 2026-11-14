package com.example.robottirabasura.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.repository.RobotRepository
import com.example.robottirabasura.domain.usecase.ObserveRobotStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ShellUiState(
    val robotAlias: String? = null,
    val link: LinkState = LinkState.DESCONECTADO,
    val ageSeconds: Long? = null,
    val openAlerts: Int = 0,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val robot: RobotRepository,
    observeStatus: ObserveRobotStatusUseCase,
) : ViewModel() {

    val uiState: StateFlow<ShellUiState> = combine(robot.connectedRobot, observeStatus()) { connected, status ->
        ShellUiState(
            robotAlias = connected?.alias,
            link = status.link,
            ageSeconds = status.ageMillis?.div(1000),
            openAlerts = status.telemetry?.unacknowledgedAlerts?.size ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState())

    fun onDisconnect() = robot.disconnect()
}
