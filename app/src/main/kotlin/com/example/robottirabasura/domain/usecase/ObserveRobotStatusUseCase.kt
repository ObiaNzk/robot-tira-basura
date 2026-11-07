package com.example.robottirabasura.domain.usecase

import com.example.robottirabasura.domain.model.Freshness
import com.example.robottirabasura.domain.model.LinkState
import com.example.robottirabasura.domain.model.Telemetry
import com.example.robottirabasura.domain.repository.Clock
import com.example.robottirabasura.domain.repository.RobotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Telemetry plus how old it is, recomputed every second even when nothing new arrives (that's how staleness shows). */
data class RobotStatus(val telemetry: Telemetry?, val link: LinkState, val ageMillis: Long?)

class ObserveRobotStatusUseCase @Inject constructor(
    private val robot: RobotRepository,
    private val clock: Clock,
) {
    operator fun invoke(tickMillis: Long = 1_000): Flow<RobotStatus> {
        val ticker = flow {
            while (true) {
                emit(clock.nowMillis())
                delay(tickMillis)
            }
        }
        return combine(robot.telemetry, robot.lastReceivedAtMillis, ticker) { telemetry, receivedAt, _ ->
            val now = clock.nowMillis()
            RobotStatus(
                telemetry = telemetry,
                link = Freshness.linkState(receivedAt, now),
                ageMillis = receivedAt?.let { (now - it).coerceAtLeast(0) },
            )
        }
    }
}
