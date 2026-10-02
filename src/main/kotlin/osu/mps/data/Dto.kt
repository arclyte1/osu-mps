package osu.mps.data

import kotlinx.serialization.Serializable

@Serializable
data class MatchDto(
    val id: Int,
    val name: String? = null,
    val startTime: String,
    val endTime: String? = null,
)

@Serializable
data class EventDto(
    val matchId: Int,
    val id: Long,
    val type: EventType,
    val userId: Int? = null,
    val deltatime: Int,
)

@Serializable
data class GameDto(
    val matchId: Int,
    val id: Long,
    val eventId: Long,
    val startDeltatime: Int,
    val endDeltatime: Int? = null,
    val mods: Int,
    val gameMode: GameMode,
    val beatmapId: Int,
    val scoringType: ScoringType,
    val teamType: TeamType,
)

@Serializable
data class ScoreDto(
    val matchId: Int,
    val gameId: Long,
    val userId: Int,
    val score: Int,
    val mods: Int,
    val maxCombo: Short,
    val count100: Short,
    val count300: Short,
    val count50: Short,
    val countGeki: Short,
    val countKatu: Short,
    val countMiss: Short,
    val passed: Boolean,
    val matchSlot: Short,
    val matchTeam: MatchTeam,
    val matchPass: Boolean,
)

@Serializable
data class MatchParserQueueDto(
    val matchId: Int,
    val lastChecked: String,
    val matchStatus: MatchParserQueueStatus,
    val lastParsedEventId: Long,
    val errorText: String? = null,
    val rawJson: String? = null,
)

@Serializable
data class PlayerDto(
    val id: Int,
    val username: String? = null,
    val countryCode: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class BeatmapsetDto(
    val id: Int,
    val artist: String? = null,
    val artistUnicode: String? = null,
    val title: String? = null,
    val titleUnicode: String? = null,
    val creatorId: Int? = null,
    val creatorName: String? = null,
    val genreId: Int? = null,
    val languageId: Int? = null,
)

@Serializable
data class BeatmapDto(
    val id: Int,
    val beatmapsetId: Int,
    val status: String? = null,
    val difficultyRating: Float? = null,
    val version: String? = null,
    val mode: GameMode? = null,
    val totalLength: Int? = null,
    val creatorId: Int? = null,
)

@Serializable
data class MatchTitleChangeDto(
    val matchId: Int,
    val eventId: Long,
    val timestamp: String,
    val title: String,
)

private val MODS = mapOf(
    "NM" to 0,
    "NF" to 1,
    "EZ" to 2,
    "TD" to 4,
    "HD" to 8,
    "HR" to 16,
    "SD" to 32,
    "DT" to 64,
    "RX" to 128,
    "HT" to 256,
    "NC" to 512,
    "FL" to 1024,
    "Autoplay" to 2048,
    "SO" to 4096,
    "AP" to 8192,
    "PF" to 16384,
    "4K" to 32768,
    "5K" to 65536,
    "6K" to 131072,
    "7K" to 262144,
    "8K" to 524288,
    "FI" to 1048576,
    "RD" to 2097152,
    "LastMod" to 4194304,
    "9K" to 16777216,
    "10K" to 33554432,
    "1K" to 67108864,
    "3K" to 134217728,
    "2K" to 268435456,
    "ScoreV2" to 536870912,
    "MR" to 1073741824
)

fun modsListToInt(mods: List<String?>): Int? {
    var modsValue = 0
    mods.forEach {
        modsValue += MODS[it] ?: return null
    }
    return modsValue
}