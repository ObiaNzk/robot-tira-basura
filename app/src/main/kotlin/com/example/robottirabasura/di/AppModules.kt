package com.example.robottirabasura.di

import android.content.Context
import androidx.room.Room
import com.example.robottirabasura.BuildConfig
import com.example.robottirabasura.data.history.HistoryRecorder
import com.example.robottirabasura.data.mirror.HttpVisorSender
import com.example.robottirabasura.data.mirror.VisorMirror
import com.example.robottirabasura.data.history.RoomHistoryRepository
import com.example.robottirabasura.data.local.HistoryDao
import com.example.robottirabasura.data.local.RobotDatabase
import com.example.robottirabasura.data.sim.SimulatedRobotRepository
import com.example.robottirabasura.domain.repository.Clock
import com.example.robottirabasura.domain.repository.HistoryRepository
import com.example.robottirabasura.domain.repository.RobotRepository
import com.example.robottirabasura.domain.repository.SimulatorControls
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope that lives as long as the app: the simulated robot and the history recorder run in it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun clock(): Clock = Clock { System.currentTimeMillis() }
}

@Module
@InstallIn(SingletonComponent::class)
object RobotModule {
    @Provides
    @Singleton
    fun simulatedRobot(@ApplicationScope scope: CoroutineScope, clock: Clock): SimulatedRobotRepository =
        SimulatedRobotRepository(scope, clock, newId = { UUID.randomUUID().toString() })

    // Swap these two bindings to talk to a real robot gateway; nothing above the repository changes.
    @Provides
    fun robotRepository(simulated: SimulatedRobotRepository): RobotRepository = simulated

    @Provides
    fun simulatorControls(simulated: SimulatedRobotRepository): SimulatorControls = simulated
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RobotDatabase =
        Room.databaseBuilder(context, RobotDatabase::class.java, "robot-tira-basura.db").build()

    @Provides
    fun historyDao(db: RobotDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun historyRecorder(
        robot: RobotRepository,
        history: HistoryRepository,
        @ApplicationScope scope: CoroutineScope,
    ): HistoryRecorder = HistoryRecorder(robot, history, scope)
}

@Module
@InstallIn(SingletonComponent::class)
object VisorModule {
    @Provides
    @Singleton
    fun visorMirror(robot: RobotRepository, @ApplicationScope scope: CoroutineScope): VisorMirror =
        VisorMirror(robot, BuildConfig.MUJOCO_VISOR_URL.takeIf { it.isNotBlank() }?.let(::HttpVisorSender), scope)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryBindings {
    @Binds
    abstract fun historyRepository(impl: RoomHistoryRepository): HistoryRepository
}
