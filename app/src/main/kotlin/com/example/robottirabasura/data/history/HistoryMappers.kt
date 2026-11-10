package com.example.robottirabasura.data.history

import com.example.robottirabasura.data.local.EventEntity
import com.example.robottirabasura.data.local.MissionEntity
import com.example.robottirabasura.domain.model.AlertSeverity
import com.example.robottirabasura.domain.model.EventType
import com.example.robottirabasura.domain.model.MissionRecord
import com.example.robottirabasura.domain.model.MissionResult
import com.example.robottirabasura.domain.model.RobotEvent

fun RobotEvent.toEntity() = EventEntity(
    timestamp = timestampMillis, type = type.name, severity = severity.name, detail = detail, missionId = missionId,
)

/** Rows written by an older version with an unknown type are kept and shown as generic events. */
fun EventEntity.toDomain() = RobotEvent(
    timestampMillis = timestamp,
    type = enumValueOrNull<EventType>(type) ?: EventType.COMANDO,
    severity = enumValueOrNull<AlertSeverity>(severity) ?: AlertSeverity.INFO,
    detail = detail,
    missionId = missionId,
)

fun MissionRecord.toEntity() = MissionEntity(
    id = id, startedAt = startedAtMillis, endedAt = endedAtMillis, result = result?.name, bolsasId = bolsasId,
    tachoId = tachoId, distanceMeters = distanceMeters,
)

fun MissionEntity.toDomain() = MissionRecord(
    id = id, startedAtMillis = startedAt, endedAtMillis = endedAt, result = result?.let { enumValueOrNull<MissionResult>(it) },
    bolsasId = bolsasId, tachoId = tachoId, distanceMeters = distanceMeters,
)

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? = enumValues<T>().firstOrNull { it.name == name }
