package osu.mps.app.di

import org.koin.dsl.module
import osu.mps.config.AppConfig
import osu.mps.data.BeatmapRepository
import osu.mps.data.BeatmapsetRepository
import osu.mps.data.EventRepository
import osu.mps.data.GameRepository
import osu.mps.data.MatchParserQueueRepository
import osu.mps.data.MatchRepository
import osu.mps.data.MatchTitleChangeRepository
import osu.mps.data.PlayerRepository
import osu.mps.data.ScoreRepository
import osu.mps.osu.OsuApiService
import osu.mps.osu.jobs.MatchesSyncScheduler

fun appModule(parserVersion: Short) = module {
    single { MatchRepository(parserVersion) }
    single { GameRepository() }
    single { ScoreRepository() }
    single { EventRepository() }
    single { PlayerRepository() }
    single { BeatmapsetRepository() }
    single { BeatmapRepository() }
    single { MatchParserQueueRepository() }
    single { MatchTitleChangeRepository() }
    single {
        OsuApiService(
            clientId = AppConfig.osuClientId(),
            clientSecret = AppConfig.osuClientSecret(),
        )
    }
    single {
        MatchesSyncScheduler(
            matchRepository = get(),
            gameRepository = get(),
            scoreRepository = get(),
            eventRepository = get(),
            playerRepository = get(),
            beatmapsetRepository = get(),
            beatmapRepository = get(),
            matchParserQueueRepository = get(),
            matchTitleChangeRepository = get(),
            osuService = get(),
        )
    }
}