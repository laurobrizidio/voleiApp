package br.com.voleipelada.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val consecutiveWins: Int,
    val status: String,
    val queuePosition: Int,
    val createdAt: Long,
)

@Entity(tableName = "players", foreignKeys = [
    ForeignKey(entity = TeamEntity::class, parentColumns = ["id"], childColumns = ["assignedTeamId"],
        onDelete = ForeignKey.SET_NULL),
], indices = [Index("assignedTeamId")])
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val skillLevel: Int = 3,
    val isPresent: Boolean = true,
    val assignedTeamId: Long?,
    @ColumnInfo(defaultValue = "'UNSPECIFIED'") val sex: String = "UNSPECIFIED",
)

@Entity(tableName = "rotation_rules")
data class RotationRulesEntity(
    @PrimaryKey val id: Int = 1,
    val targetPoints: Int = 15,
    val winByTwo: Boolean = true,
    val maxPointsCap: Int? = null,
    val maxConsecutiveWins: Int? = 2,
    val championReturnPolicy: String = "FIRST_IN_QUEUE",
    val fixedReturnPosition: Int = 2,
    val keepScreenOn: Boolean = true,
    val enableSoundAndHaptics: Boolean = true,
    val bothLeave: Boolean = false,
    val tieRule: String = "BOTH_LEAVE",
)

@Entity(tableName = "match_history")
data class MatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamAId: Long,
    val teamAName: String,
    val teamAScore: Int,
    val teamBId: Long,
    val teamBName: String,
    val teamBScore: Int,
    val winnerTeamId: Long?,
    val loserTeamId: Long?,
    val championExitedByStreakLimit: Boolean,
    val exitReasonSummary: String,
    val durationSeconds: Long,
    val finishedAt: Long,
    val teamAReason: String,
    val teamBReason: String,
    @ColumnInfo(defaultValue = "1") val visibleInHistory: Boolean = true,
    @ColumnInfo(defaultValue = "1") val countsForStandings: Boolean = true,
)

@Entity(tableName = "active_match_state")
data class ActiveMatchStateEntity(
    @PrimaryKey val id: Int = 1,
    val teamAId: Long? = null,
    val teamBId: Long? = null,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val servingTeam: String = "NONE",
    val sidesSwapped: Boolean = false,
    val elapsedSeconds: Long = 0,
    val elapsedRemainderMs: Long = 0,
    val startedAt: Long? = null,
    val pointHistoryStackJson: String = "[]",
    val round: Long = 0,
)
