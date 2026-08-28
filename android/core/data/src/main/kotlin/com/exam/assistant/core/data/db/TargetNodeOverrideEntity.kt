package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import com.exam.assistant.domain.TargetNodeOverride
import com.exam.assistant.domain.TargetNodeState

@Entity(tableName = "target_node_override", primaryKeys = ["attemptId", "nodeId"])
data class TargetNodeOverrideEntity(
    val attemptId: String,
    val nodeId: String,
    val state: String,
    val updatedAtEpochMs: Long,
)

fun TargetNodeOverrideEntity.toDomain() = TargetNodeOverride(
    attemptId = attemptId,
    nodeId = nodeId,
    state = TargetNodeState.valueOf(state),
    updatedAtEpochMs = updatedAtEpochMs,
)

fun TargetNodeOverride.toEntity() = TargetNodeOverrideEntity(
    attemptId = attemptId,
    nodeId = nodeId,
    state = state.name,
    updatedAtEpochMs = updatedAtEpochMs,
)

@Dao
interface TargetNodeOverrideDao {
    @Upsert
    suspend fun upsert(entity: TargetNodeOverrideEntity)

    @Query("SELECT * FROM target_node_override WHERE attemptId = :attemptId")
    suspend fun allOnce(attemptId: String): List<TargetNodeOverrideEntity>

    @Query("DELETE FROM target_node_override WHERE attemptId = :attemptId AND nodeId = :nodeId")
    suspend fun delete(attemptId: String, nodeId: String)

    @Query("DELETE FROM target_node_override WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)
}
