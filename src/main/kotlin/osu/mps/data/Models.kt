package osu.mps.data

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.DdlAware
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.postgresql.util.PGobject
import osu.mps.data.BeatmapTable.id
import osu.mps.data.GameTable.beatmapId

private class PgDbEnum(enumTypeName: String, dbValue: String) : PGobject() {
    init {
        type = enumTypeName
        value = dbValue
    }
}

private interface DbEnumValue {
    val dbValue: String
}

enum class EventType(override val dbValue: String) : DbEnumValue {
    MATCH_CREATED("match-created"),
    MATCH_DISBANDED("match-disbanded"),
    PLAYER_JOINED("player-joined"),
    PLAYER_LEFT("player-left"),
    HOST_CHANGED("host-changed"),
    PLAYER_KICKED("player-kicked"),
    UNKNOWN("unknown");

    companion object {
        fun dbValueOf(dbValue: String): EventType? {
            return EventType.entries.find { it.dbValue == dbValue }
        }
    }
}

enum class GameMode(override val dbValue: String) : DbEnumValue {
    OSU("osu"),
    FRUITS("fruits"),
    MANIA("mania"),
    TAIKO("taiko");

    companion object {
        fun dbValueOf(dbValue: String): GameMode? {
            return GameMode.entries.find { it.dbValue == dbValue }
        }
    }
}

enum class ScoringType(override val dbValue: String) : DbEnumValue {
    SCORE("score"),
    COMBO("combo"),
    ACCURACY("accuracy"),
    SCOREV2("scorev2");

    companion object {
        fun dbValueOf(dbValue: String): ScoringType? {
            return ScoringType.entries.find { it.dbValue == dbValue }
        }
    }
}

enum class TeamType(override val dbValue: String) : DbEnumValue {
    HEAD_TO_HEAD("head-to-head"),
    TAG_COOP("tag-coop"),
    TEAM_VS("team-vs"),
    TAG_TEAM_VS("tag-team-vs");

    companion object {
        fun dbValueOf(dbValue: String): TeamType? {
            return TeamType.entries.find { it.dbValue == dbValue }
        }
    }
}

enum class MatchParserQueueStatus(override val dbValue: String) : DbEnumValue {
    UNCHECKED("unchecked"),
    ONGOING("ongoing"),
    ERROR("error"),
}

enum class MatchTeam(override val dbValue: String) : DbEnumValue {
    NONE("none"),
    RED("red"),
    BLUE("blue");

    companion object {
        fun dbValueOf(dbValue: String): MatchTeam? {
            return MatchTeam.entries.find { it.dbValue == dbValue }
        }
    }
}

private inline fun <reified T> Table.pgEnum(name: String, typeName: String): Column<T>
    where T : Enum<T>, T : DbEnumValue {
    val byDbValue = enumValues<T>().associateBy { it.dbValue }
    return customEnumeration(
        name = name,
        sql = typeName,
        fromDb = { value -> byDbValue[value as String] ?: error("Unknown $typeName value: $value") },
        toDb = { PgDbEnum(typeName, it.dbValue) },
    )
}

private fun postgresEnumDdl(typeName: String, values: Collection<String>): String {
    val enumValues = values.joinToString { "'$it'" }
    val tag = "${'$'}${'$'}"
    return """
        DO $tag BEGIN
            CREATE TYPE $typeName AS ENUM ($enumValues);
        EXCEPTION
            WHEN duplicate_object THEN NULL;
        END $tag;
    """.trimIndent()
}

abstract class RawDdl(
    private val create: List<String>,
    private val drop: List<String> = emptyList(),
) : DdlAware {
    override fun createStatement(): List<String> = create
    override fun dropStatement(): List<String> = drop
    override fun modifyStatement(): List<String> = emptyList()
}

object PgTrgmExtension : RawDdl(
    create = listOf("CREATE EXTENSION IF NOT EXISTS pg_trgm"),
)

object EventTypeEnumDdl : RawDdl(
    create = listOf(postgresEnumDdl("eventtype", EventType.entries.map { it.dbValue })),
    drop = listOf("DROP TYPE IF EXISTS eventtype"),
)

object GameModeEnumDdl : RawDdl(
    create = listOf(postgresEnumDdl("gamemode", GameMode.entries.map { it.dbValue })),
    drop = listOf("DROP TYPE IF EXISTS gamemode"),
)

object ScoringTypeEnumDdl : RawDdl(
    create = listOf(postgresEnumDdl("scoringtype", ScoringType.entries.map { it.dbValue })),
    drop = listOf("DROP TYPE IF EXISTS scoringtype"),
)

object TeamTypeEnumDdl : RawDdl(
    create = listOf(postgresEnumDdl("teamtype", TeamType.entries.map { it.dbValue })),
    drop = listOf("DROP TYPE IF EXISTS teamtype"),
)

object MatchParserQueueStatusEnumDdl : RawDdl(
    create = listOf(postgresEnumDdl("match_parser_queue_status", MatchParserQueueStatus.entries.map { it.dbValue })),
    drop = listOf("DROP TYPE IF EXISTS match_parser_queue_status"),
)

object MatchTeamEnumDdl : RawDdl(
    create = listOf(postgresEnumDdl("matchteam", MatchTeam.entries.map { it.dbValue })),
    drop = listOf("DROP TYPE IF EXISTS matchteam"),
)

object MatchNameTrgmIndex : RawDdl(
    create = listOf("CREATE INDEX IF NOT EXISTS match_name ON match USING gin (name gin_trgm_ops)"),
    drop = listOf("DROP INDEX IF EXISTS match_name"),
)

object MatchTable : Table("match") {
    val id = integer("id")
    val name = text("name").nullable()
    val startTime = timestamp("start_time").nullable()
    val endTime = timestamp("end_time").nullable()

    override val primaryKey = PrimaryKey(id)
}

object EventTable : Table("event") {
    val matchId = integer("match_id").references(MatchTable.id)
    val id = long("id")
    val type = pgEnum<EventType>("type", "eventtype")
    val userId = integer("user_id").nullable()
    val deltatime = integer("deltatime")

    override val primaryKey = PrimaryKey(matchId, id)

    init {
        index(customIndexName = "event_user_id", isUnique = false, userId)
    }
}

object GameTable : Table("game") {
    val matchId = integer("match_id").references(MatchTable.id)
    val id = long("id")
    val eventId = long("event_id")
    val startDeltatime = integer("start_deltatime")
    val endDeltatime = integer("end_deltatime").nullable()
    val mods = integer("mods")
    val gameMode = pgEnum<GameMode>("game_mode", "gamemode")
    val beatmapId = integer("beatmap_id")
    val scoringType = pgEnum<ScoringType>("scoring_type", "scoringtype")
    val teamType = pgEnum<TeamType>("team_type", "teamtype")

    override val primaryKey = PrimaryKey(matchId, id)

    init {
        index(customIndexName = "game_beatmap_id", isUnique = false, beatmapId)
    }
}

object ScoreTable : Table("score") {
    val matchId = integer("match_id")
    val gameId = long("game_id")
    val userId = integer("user_id")
    val score = integer("score")
    val mods = integer("mods")
    val maxCombo = short("max_combo")
    val count100 = short("count_100")
    val count300 = short("count_300")
    val count50 = short("count_50")
    val countGeki = short("count_geki")
    val countKatu = short("count_katu")
    val countMiss = short("count_miss")
    val passed = bool("passed")
    val matchSlot = short("match_slot")
    val matchTeam = pgEnum<MatchTeam>("match_team", "matchteam")
    val matchPass = bool("match_pass")

    override val primaryKey = PrimaryKey(matchId, gameId, userId)

    init {
        foreignKey(matchId, gameId, target = GameTable.primaryKey)
        index(customIndexName = "score_user_id", isUnique = false, userId)
    }
}

object MatchParserQueueTable : Table("match_parser_queue") {
    val matchId = integer("match_id")
    val lastChecked = timestamp("last_checked")
    val matchStatus = pgEnum<MatchParserQueueStatus>("match_status", "match_parser_queue_status")
    val lastParsedEventId = long("last_parsed_event_id")
    val errorText = text("error_text").nullable()
    val rawJson = text("raw_json").nullable()

    override val primaryKey = PrimaryKey(matchId)
}

object PlayerTable : Table("player") {
    val id = integer("id")
    val username = text("username").nullable()
    val countryCode = text("country_code").nullable()
    val avatarUrl = text("avatar_url").nullable()

    override val primaryKey = PrimaryKey(id)
}

object BeatmapsetTable : Table("beatmapset") {
    val id = integer("id")
    val artist = text("artist").nullable()
    val artistUnicode = text("artist_unicode").nullable()
    val title = text("title").nullable()
    val titleUnicode = text("title_unicode").nullable()
    val creatorId = integer("creator_id").nullable()
    val creatorName = text("creator_name").nullable()
    val genreId = integer("genre_id").nullable()
    val languageId = integer("language_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object BeatmapTable : Table("beatmap") {
    val id = integer("id")
    val beatmapsetId = integer("beatmapset_id").references(BeatmapsetTable.id)
    val status = text("status").nullable()
    val difficultyRating = float("difficulty_rating").nullable()
    val version = text("version").nullable()
    val mode = pgEnum<GameMode>("game_mode", "gamemode").nullable()
    val totalLength = integer("total_length").nullable()
    val creatorId = integer("creator_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object MatchTitleChange : Table("match_title_change") {
    val matchId = integer("match_id")
    val eventId = long("event_id")
    val timestamp = timestamp("timestamp")
    val title = text("title")

    override val primaryKey = PrimaryKey(eventId)

    init {
        index(customIndexName = "match_title_change_match_id", isUnique = false, matchId)
    }
}

val AllTables = arrayOf(
    MatchTable,
    EventTable,
    GameTable,
    ScoreTable,
    MatchParserQueueTable,
    PlayerTable,
    BeatmapsetTable,
    BeatmapTable,
    MatchTitleChange,
)

val SchemaPreStatements: List<DdlAware> = listOf(
    PgTrgmExtension,
    EventTypeEnumDdl,
    GameModeEnumDdl,
    ScoringTypeEnumDdl,
    TeamTypeEnumDdl,
    MatchParserQueueStatusEnumDdl,
    MatchTeamEnumDdl,
)

val SchemaPostStatements: List<DdlAware> = listOf(
    MatchNameTrgmIndex,
)
