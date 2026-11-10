package com.example.robottirabasura.data.history

import com.example.robottirabasura.domain.repository.HistoryRepository
import com.example.robottirabasura.domain.repository.RobotRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Writes every robot event and mission update to the local history, for the whole life of the app. */
class HistoryRecorder(
    private val robot: RobotRepository,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { robot.events.collect { safely { history.record(it) } } }
        scope.launch { robot.missionUpdates.collect { safely { history.saveMission(it) } } }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed write (disk full) must not stop the recorder; the robot keeps working.
        }
    }
}
