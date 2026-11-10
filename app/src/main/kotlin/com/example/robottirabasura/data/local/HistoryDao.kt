package com.example.robottirabasura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeEvents(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM missions ORDER BY started_at DESC")
    fun observeMissions(): Flow<List<MissionEntity>>

    // Events are append-only: a new row each time.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: EventEntity)

    // A mission is written when it starts and replaced when it ends.
    @Upsert
    suspend fun upsertMission(mission: MissionEntity)

    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("DELETE FROM missions")
    suspend fun clearMissions()
}
