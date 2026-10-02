package osu.mps.data

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class MatchRepository {
    fun upsert(vararg matches: MatchDto): Int = transaction {
        MatchTable.upsertCount(matches) { match ->
            this[MatchTable.id] = match.id
            this[MatchTable.name] = match.name
            this[MatchTable.startTime] = match.startTime.toInstantOrNull()
            this[MatchTable.endTime] = match.endTime.toInstantOrNull()
        }
    }

    fun maxId(): Int? = transaction {
        val maxId = MatchTable.id.max().alias("max_id")
        MatchTable.select(maxId).firstOrNull()?.get(maxId)
    }
}

class GameRepository {
    fun upsert(vararg games: GameDto): Int = transaction {
        GameTable.upsertCount(games) { game ->
            this[GameTable.matchId] = game.matchId
            this[GameTable.id] = game.id
            this[GameTable.eventId] = game.eventId
            this[GameTable.startDeltatime] = game.startDeltatime
            this[GameTable.endDeltatime] = game.endDeltatime
            this[GameTable.mods] = game.mods
            this[GameTable.gameMode] = game.gameMode
            this[GameTable.beatmapId] = game.beatmapId
            this[GameTable.scoringType] = game.scoringType
            this[GameTable.teamType] = game.teamType
        }
    }
}

class ScoreRepository {
    fun upsert(vararg scores: ScoreDto): Int = transaction {
        ScoreTable.upsertCount(scores) { score ->
            this[ScoreTable.matchId] = score.matchId
            this[ScoreTable.gameId] = score.gameId
            this[ScoreTable.userId] = score.userId
            this[ScoreTable.score] = score.score
            this[ScoreTable.mods] = score.mods
            this[ScoreTable.maxCombo] = score.maxCombo
            this[ScoreTable.count100] = score.count100
            this[ScoreTable.count300] = score.count300
            this[ScoreTable.count50] = score.count50
            this[ScoreTable.countGeki] = score.countGeki
            this[ScoreTable.countKatu] = score.countKatu
            this[ScoreTable.countMiss] = score.countMiss
            this[ScoreTable.passed] = score.passed
            this[ScoreTable.matchSlot] = score.matchSlot
            this[ScoreTable.matchTeam] = score.matchTeam
            this[ScoreTable.matchPass] = score.matchPass
        }
    }
}

class EventRepository {
    fun upsert(vararg events: EventDto): Int = transaction {
        EventTable.upsertCount(events) { event ->
            this[EventTable.matchId] = event.matchId
            this[EventTable.id] = event.id
            this[EventTable.type] = event.type
            this[EventTable.userId] = event.userId
            this[EventTable.deltatime] = event.deltatime
        }
    }
}

class PlayerRepository {
    fun upsert(vararg players: PlayerDto): Int = transaction {
        PlayerTable.upsertCount(players) { player ->
            this[PlayerTable.id] = player.id
            player.username?.let { this[PlayerTable.username] = it }
            player.countryCode?.let { this[PlayerTable.countryCode] = it }
            player.avatarUrl?.let { this[PlayerTable.avatarUrl] = it }
        }
    }
}

class BeatmapsetRepository {
    fun upsert(vararg beatmapsets: BeatmapsetDto): Int = transaction {
        BeatmapsetTable.upsertCount(beatmapsets) { beatmapset ->
            this[BeatmapsetTable.id] = beatmapset.id
            beatmapset.artist?.let { this[BeatmapsetTable.artist] = it }
            beatmapset.artistUnicode?.let { this[BeatmapsetTable.artistUnicode] = it }
            beatmapset.title?.let { this[BeatmapsetTable.title] = it }
            beatmapset.titleUnicode?.let { this[BeatmapsetTable.titleUnicode] = it }
            beatmapset.creatorId?.let { this[BeatmapsetTable.creatorId] = it }
            beatmapset.creatorName?.let { this[BeatmapsetTable.creatorName] = it }
            beatmapset.genreId?.let { this[BeatmapsetTable.genreId] = it }
            beatmapset.languageId?.let { this[BeatmapsetTable.languageId] = it }
        }
    }
}

class BeatmapRepository {
    fun upsert(vararg beatmaps: BeatmapDto): Int = transaction {
        BeatmapTable.upsertCount(beatmaps) { beatmap ->
            this[BeatmapTable.id] = beatmap.id
            this[BeatmapTable.beatmapsetId] = beatmap.beatmapsetId
            beatmap.status?.let { this[BeatmapTable.status] = it }
            beatmap.difficultyRating?.let { this[BeatmapTable.difficultyRating] = it }
            beatmap.version?.let { this[BeatmapTable.version] = it }
            beatmap.mode?.let { this[BeatmapTable.mode] = it }
            beatmap.totalLength?.let { this[BeatmapTable.totalLength] = it }
            beatmap.creatorId?.let { this[BeatmapTable.creatorId] = it }
        }
    }
}

class MatchParserQueueRepository {
    fun upsert(vararg entries: MatchParserQueueDto): Int = transaction {
        MatchParserQueueTable.upsertCount(entries) { entry ->
            this[MatchParserQueueTable.matchId] = entry.matchId
            this[MatchParserQueueTable.lastChecked] = Instant.parse(entry.lastChecked)
            this[MatchParserQueueTable.matchStatus] = entry.matchStatus
            this[MatchParserQueueTable.lastParsedEventId] = entry.lastParsedEventId
            this[MatchParserQueueTable.errorText] = entry.errorText
            this[MatchParserQueueTable.rawJson] = entry.rawJson
        }
    }

    fun remove(matchId: Int) = transaction {
        MatchParserQueueTable.deleteWhere { MatchParserQueueTable.matchId eq matchId }
    }

    fun maxId(): Int? = transaction {
        val maxId = MatchParserQueueTable.matchId.max().alias("max_id")
        MatchParserQueueTable.select(maxId).firstOrNull()?.get(maxId)
    }

    fun selectOldestChecked(limit: Int = 100): List<MatchParserQueueDto> = transaction {
        MatchParserQueueTable.selectAll()
            .orderBy(MatchParserQueueTable.lastChecked, SortOrder.ASC)
            .limit(limit)
            .map { row ->
                MatchParserQueueDto(
                    matchId = row[MatchParserQueueTable.matchId],
                    lastChecked = row[MatchParserQueueTable.lastChecked].toString(),
                    matchStatus = row[MatchParserQueueTable.matchStatus],
                    lastParsedEventId = row[MatchParserQueueTable.lastParsedEventId],
                    errorText = row[MatchParserQueueTable.errorText],
                    rawJson = row[MatchParserQueueTable.rawJson],
                )
            }
    }
}

class MatchTitleChangeRepository {

    fun upsert(vararg entries: MatchTitleChangeDto): Int = transaction {
        MatchTitleChange.upsertCount(entries) { entry ->
            this[MatchTitleChange.matchId] = entry.matchId
            this[MatchTitleChange.eventId] = entry.eventId
            this[MatchTitleChange.timestamp] = Instant.parse(entry.timestamp)
            this[MatchTitleChange.title] = entry.title
        }
    }

    fun getLastMatchTitleChange(matchId: Int) = transaction {
        MatchTitleChange.selectAll()
            .where { MatchTitleChange.matchId eq matchId }
            .orderBy(MatchTitleChange.eventId, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toMatchTitleChange()
    }

    private fun ResultRow.toMatchTitleChange(): MatchTitleChangeDto = MatchTitleChangeDto(
        matchId = this[MatchTitleChange.matchId],
        eventId = this[MatchTitleChange.eventId],
        timestamp = this[MatchTitleChange.timestamp].toString(),
        title = this[MatchTitleChange.title],
    )
}
