package com.example.robottirabasura.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EventEntity::class, MissionEntity::class], version = 1, exportSchema = true)
abstract class RobotDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
