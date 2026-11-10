package com.example.robottirabasura.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "events", indices = [Index("timestamp")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val severity: String,
    val detail: String,
    @ColumnInfo(name = "mission_id") val missionId: String?,
)

@Entity(tableName = "missions", indices = [Index("started_at")])
data class MissionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    val result: String?,
    @ColumnInfo(name = "bolsas_id") val bolsasId: String,
    @ColumnInfo(name = "tacho_id") val tachoId: String,
    @ColumnInfo(name = "distance_m") val distanceMeters: Double,
)
