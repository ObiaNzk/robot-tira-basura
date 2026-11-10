package com.example.robottirabasura.data.history

import com.example.robottirabasura.data.local.HistoryDao
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.RobotEvent
import com.example.robottirabasura.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomHistoryRepository @Inject constructor(private val dao: HistoryDao) : HistoryRepository {
    override fun observeEvents(limit: Int): Flow<List<RobotEvent>> =
        dao.observeEvents(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeMissions(): Flow<List<MissionRecord>> =
        dao.observeMissions().map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(event: RobotEvent) = dao.insertEvent(event.toEntity())

    override suspend fun saveMission(mission: MissionRecord) = dao.upsertMission(mission.toEntity())

    override suspend fun clear() {
        dao.clearEvents()
        dao.clearMissions()
    }
}
