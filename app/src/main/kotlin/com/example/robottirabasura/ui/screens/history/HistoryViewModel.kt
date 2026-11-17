package com.example.robottirabasura.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryTab { MISIONES, EVENTOS }

sealed interface HistoryContent {
    data object Loading : HistoryContent
    data class Loaded(val missions: List<MissionRecord>, val events: List<RobotEvent>) : HistoryContent
}

data class HistoryUiState(
    val tab: HistoryTab = HistoryTab.MISIONES,
    val content: HistoryContent = HistoryContent.Loading,
    val confirmingClear: Boolean = false,
)

private data class HistoryUi(val tab: HistoryTab = HistoryTab.MISIONES, val confirmingClear: Boolean = false)

@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: HistoryRepository) : ViewModel() {

    private val ui = MutableStateFlow(HistoryUi())

    val uiState: StateFlow<HistoryUiState> =
        combine(ui, history.observeMissions(), history.observeEvents()) { u, missions, events ->
            HistoryUiState(u.tab, HistoryContent.Loaded(missions, events), u.confirmingClear)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onTab(tab: HistoryTab) = ui.update { it.copy(tab = tab) }

    fun onClear() = ui.update { it.copy(confirmingClear = true) }

    fun onDismissClear() = ui.update { it.copy(confirmingClear = false) }

    fun onConfirmClear() {
        ui.update { it.copy(confirmingClear = false) }
        viewModelScope.launch { history.clear() }
    }
}
