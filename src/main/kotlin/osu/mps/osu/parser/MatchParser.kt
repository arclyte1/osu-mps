package osu.mps.osu.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import osu.mps.core.json.JsonCursor
import osu.mps.core.json.JsonCursorException
import osu.mps.core.json.get
import osu.mps.core.json.longOrNull
import osu.mps.core.json.requireEnum
import osu.mps.core.minus
import osu.mps.core.toInstant
import osu.mps.data.BeatmapDto
import osu.mps.data.BeatmapsetDto
import osu.mps.data.EventDto
import osu.mps.data.EventType
import osu.mps.data.GameDto
import osu.mps.data.GameMode
import osu.mps.data.MatchDto
import osu.mps.data.MatchTeam
import osu.mps.data.MatchTitleChangeDto
import osu.mps.data.PlayerDto
import osu.mps.data.ScoreDto
import osu.mps.data.ScoringType
import osu.mps.data.TeamType
import osu.mps.data.modsListToInt
import osu.mps.osu.GetMatchResult

class MatchParser(val jsonText: String, val lastParsedMatchTitle: String?) {

    val json = Json { ignoreUnknownKeys = true }

    fun parse(): GetMatchResult {
        val jsonObject = json.parseToJsonElement(jsonText)

        if (jsonObject !is JsonObject || jsonObject.isEmpty()) {
            return GetMatchResult.Failure("Invalid json", jsonText)
        }

        try {
            val jsonCursor = JsonCursor.root(jsonObject)
            val match = parseMatch(jsonCursor)
            val events = parseEvents(jsonCursor, match)
            val games = parseGames(jsonCursor, match)
            val jsonEventsCount = jsonObject["events"]?.jsonArray?.count() ?: 0
            if (jsonEventsCount != games.size + events.size) {
                return GetMatchResult.Failure("Events parsing error: parsed events count hasnt match json events count", jsonText)
            }
            val scores = parseScores(jsonCursor, games)
            val players = parsePlayers(jsonCursor)
            val beatmapsets = parseBeatmapsets(jsonCursor)
            val beatmaps = parseBeatmaps(jsonCursor)
            val titleChanges = parseMatchTitleChanges(jsonCursor)
            val lastEventId = parseLastEventId(jsonCursor)
            val isReachedEnd = calculateIsReachedEnd(jsonCursor)

            return GetMatchResult.Success(
                match = match,
                games = games,
                scores = scores,
                events = events,
                players = players,
                beatmapsets = beatmapsets,
                beatmaps = beatmaps,
                matchTitleChanges = titleChanges,
                lastParsedEventId = lastEventId,
                isReachedEnd = isReachedEnd
            )
        } catch (e: JsonCursorException) {
            return GetMatchResult.Failure(e.message ?: "JSON parse error", jsonText)
        } catch (e: ResultFailureException) {
            return e.result
        } catch (e: Exception) {
            return GetMatchResult.Failure(e.message ?: "Unknown error", jsonText)
        }
    }

    private fun parseMatch(jsonCursor: JsonCursor): MatchDto {
        val match = jsonCursor["match"]
        return MatchDto(
            id = match["id"].requireInt(),
            name = match["name"].contentOrNull,
            startTime = match["start_time"].requireString(),
            endTime = match["end_time"].contentOrNull,
        )
    }

    private fun parseEvents(jsonCursor: JsonCursor, match: MatchDto): List<EventDto> {
        return jsonCursor["events"].mapNotNullArray { event ->
            if (event.containsKey("game")) return@mapNotNullArray null

            EventDto(
                matchId = match.id,
                id = event["id"].requireLong(),
                type = event["detail"]["type"].requireEnum(EventType.Companion::dbValueOf),
                userId = event["user_id"].intOrNull,
                deltatime = (event["timestamp"].requireInstant() - match.startTime.toInstant()).epochSecond.toInt()
            )
        }
    }

    private fun parseGames(jsonCursor: JsonCursor, match: MatchDto): List<GameDto> {
        return jsonCursor["events"].mapNotNullArray { event ->
            if (!event.containsKey("game")) return@mapNotNullArray null

            val game = event["game"]

            GameDto(
                matchId = match.id,
                id = game["id"].requireLong(),
                eventId = event["id"].requireLong(),
                startDeltatime = (game["start_time"].requireInstant() - match.startTime.toInstant()).epochSecond.toInt(),
                endDeltatime = game["end_time"].instantOrNull?.minus(game["start_time"].requireInstant())?.epochSecond?.toInt(),
                mods = game["mods"].mapArray(JsonCursor::requireString).let(::modsListToInt)
                    .orThrowFailureException("Unknown mods"),
                gameMode = game["mode"].requireEnum(GameMode.Companion::dbValueOf),
                beatmapId = game["beatmap_id"].requireInt(),
                scoringType = game["scoring_type"].requireEnum(ScoringType.Companion::dbValueOf),
                teamType = game["team_type"].requireEnum(TeamType.Companion::dbValueOf),
            )
        }
    }

    private fun parseScores(jsonCursor: JsonCursor, games: List<GameDto>): List<ScoreDto> {
        val gamesJson = jsonCursor["events"].filterArray { it.containsKey("game") }
        if (gamesJson.count() != games.size) throw ResultFailureException(GetMatchResult.Failure("Games sizes dont match while parsing scores", jsonText))

        return gamesJson.flatMap { gameJson ->
            val game = games.find { it.id == gameJson["game"]["id"].requireLong() }.orThrowFailureException("Game not found while parsing scores")
            gameJson["game"]["scores"].mapArray { score ->
                val statistics = score["statistics"]
                val matchInfo = score["match"]

                ScoreDto(
                    matchId = game.matchId,
                    gameId = game.id,
                    userId = score["user_id"].requireInt(),
                    score = score["score"].requireInt(),
                    mods = score["mods"].mapArray(JsonCursor::requireString).let(::modsListToInt)
                        .orThrowFailureException("Unknown mods"),
                    maxCombo = score["max_combo"].requireShort(),
                    count100 = statistics["count_100"].requireShort(),
                    count300 = statistics["count_300"].requireShort(),
                    count50 = statistics["count_50"].requireShort(),
                    countGeki = statistics["count_geki"].requireShort(),
                    countKatu = statistics["count_katu"].requireShort(),
                    countMiss = statistics["count_miss"].requireShort(),
                    passed = score["passed"].requireBoolean(),
                    matchSlot = matchInfo["slot"].requireShort(),
                    matchTeam = matchInfo["team"].requireEnum(MatchTeam.Companion::dbValueOf),
                    matchPass = matchInfo["pass"].requireBoolean(),
                )
            }
        }
    }

    private fun parseBeatmapsets(jsonCursor: JsonCursor): List<BeatmapsetDto> {
        return jsonCursor["events"]
            .mapNotNullArray { if (it.containsKey("game")) it["game"] else null }
            .mapNotNull { game ->
                val beatmap = game.opt("beatmap") ?: return@mapNotNull null
                val id = beatmap["beatmapset_id"].requireInt()
                if (id == 0) return@mapNotNull null

                val beatmapset = beatmap["beatmapset"]

                if (beatmapset.jsonObject == null) {
                    return@mapNotNull BeatmapsetDto(id = id)
                }

                BeatmapsetDto(
                    id = id,
                    artist = beatmapset["artist"].contentOrNull,
                    artistUnicode = beatmapset["artist_unicode"].contentOrNull,
                    title = beatmapset["title"].contentOrNull,
                    titleUnicode = beatmapset["title_unicode"].contentOrNull,
                    creatorId = beatmapset["user_id"].intOrNull,
                    creatorName = beatmapset["creator"].contentOrNull,
                    genreId = beatmapset["genre_id"].intOrNull,
                    languageId = beatmapset["language_id"].intOrNull,
                )
            }
    }

    private fun parseBeatmaps(jsonCursor: JsonCursor): List<BeatmapDto> {
        return jsonCursor["events"]
            .mapNotNullArray { if (it.containsKey("game")) it["game"] else null }
            .mapNotNull { game ->
                val id = game["beatmap_id"].requireInt()
                if (id == 0) return@mapNotNull null

                val beatmap = game.opt("beatmap") ?: return@mapNotNull null

                BeatmapDto(
                    id = id,
                    beatmapsetId = beatmap["beatmapset_id"].requireInt(),
                    status = beatmap["status"].contentOrNull,
                    difficultyRating = beatmap["difficulty_rating"].floatOrNull,
                    version = beatmap["version"].contentOrNull,
                    mode = beatmap["mode"].contentOrNull?.let(GameMode.Companion::dbValueOf),
                    totalLength = beatmap["total_length"].intOrNull,
                    creatorId = beatmap["user_id"].intOrNull,
                )
            }
    }

    private fun parsePlayers(jsonCursor: JsonCursor): List<PlayerDto> {
        return jsonCursor["users"].mapArray { user ->
            PlayerDto(
                id = user["id"].requireInt(),
                username = user["username"].contentOrNull,
                countryCode = user["country_code"].contentOrNull,
                avatarUrl = user["avatar_url"].contentOrNull,
            )
        }
    }

    private fun parseMatchTitleChanges(jsonCursor: JsonCursor): List<MatchTitleChangeDto> {
        var lastMatchTitle = lastParsedMatchTitle ?: jsonCursor["match"]["name"].requireString()

        val titleChanges = mutableListOf<MatchTitleChangeDto>()

        val matchId = jsonCursor["match"]["id"].requireInt()

        jsonCursor["events"].forEachArray { event ->
            val type = event["detail"]["type"].requireString()

            if (type == "other") {
                val text = event["detail"]["text"].requireString()
                if (text != lastMatchTitle) {
                    titleChanges.add(
                        MatchTitleChangeDto(
                            matchId = matchId,
                            eventId = event["id"].requireLong(),
                            timestamp = event["timestamp"].requireString(),
                            title = text
                        )
                    )
                    lastMatchTitle = text
                }
            }
        }

        return titleChanges
    }

    private fun parseLastEventId(jsonCursor: JsonCursor): Long {
        val events = jsonCursor["events"].requireArray()
        val currentGameId = jsonCursor["current_game_id"].longOrNull
        val currentGameEventJson = currentGameId?.let { gameId ->
            events.find { event ->
                event["game"]?.jsonObject?.get("id")?.longOrNull == gameId
            }?.jsonObject
        }
        val latestEventId = jsonCursor["latest_event_id"].longOrNull
        val matchEndTime = jsonCursor["match"]["end_time"].contentOrNull
        return when {
            currentGameEventJson != null && matchEndTime == null -> {
                currentGameEventJson["id"]?.longOrNull.orThrowMissingField("current game event id") - 1
            }
            events.isNotEmpty() -> {
                events.maxOfOrNull { it["id"]?.longOrNull.orThrowMissingField("events.id") }
                    .orThrowFailureException("Cannot find max event id")
            }
            latestEventId != null -> {
                latestEventId
            }
            else -> throw ResultFailureException(GetMatchResult.Failure("Unknown exception: cannot parse last event id", jsonText))
        }
    }

    private fun calculateIsReachedEnd(jsonCursor: JsonCursor): Boolean {
        if (jsonCursor["match"]["end_time"].contentOrNull == null) return false

        val maxEventId = jsonCursor["events"].requireArray().maxOfOrNull {
            it["id"]?.longOrNull.orThrowMissingField("events.id")
        }

        val mpLastEventId = jsonCursor["latest_event_id"].requireLong()

        return maxEventId == mpLastEventId
    }

    private fun <T> T?.orThrowFailureException(message: String): T {
        return this ?: throw ResultFailureException(GetMatchResult.Failure(message, jsonText))
    }

    private fun <T> T?.orThrowMissingField(fieldName: String): T {
        return this ?: throw missingFieldResultException(fieldName)
    }

    private fun missingFieldResult(field: String): GetMatchResult.Failure {
        return GetMatchResult.Failure("Missing field: $field", jsonText)
    }

    private fun missingFieldResultException(field: String): ResultFailureException {
        return ResultFailureException(missingFieldResult(field))
    }

    private class ResultFailureException(
        val result: GetMatchResult.Failure
    ) : Throwable()
}