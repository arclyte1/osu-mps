package osu.mps.app.plugins

import osu.mps.config.AppConfig
import osu.mps.data.AllTables
import osu.mps.data.BeatmapRepository
import osu.mps.data.BeatmapsetRepository
import osu.mps.data.EventRepository
import osu.mps.data.GameRepository
import osu.mps.data.MatchParserQueueRepository
import osu.mps.data.MatchRepository
import osu.mps.data.PlayerRepository
import osu.mps.data.SchemaPostStatements
import osu.mps.data.SchemaPreStatements
import osu.mps.data.ScoreRepository
import osu.mps.osu.OsuApiService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import osu.mps.data.MatchTitleChangeRepository

data class AppRepositories(
    val matchRepository: MatchRepository,
    val gameRepository: GameRepository,
    val scoreRepository: ScoreRepository,
    val eventRepository: EventRepository,
    val playerRepository: PlayerRepository,
    val beatmapsetRepository: BeatmapsetRepository,
    val beatmapRepository: BeatmapRepository,
    val matchParserQueueRepository: MatchParserQueueRepository,
    val matchTitleChangeRepository: MatchTitleChangeRepository,
    val osuService: OsuApiService,
)

fun Application.configureDatabases(): AppRepositories {
    val config = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = AppConfig.jdbcUrl()
        username = AppConfig.dbUser()
        password = AppConfig.dbPassword()
        maximumPoolSize = 16
        minimumIdle = 2
        // Exposed opens/commits transactions itself; false leaves open tx on pooled connections.
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        connectionTimeout = 30_000
        validationTimeout = 5_000
        idleTimeout = 600_000
        maxLifetime = 1_800_000
        keepaliveTime = 30_000
        addDataSourceProperty("tcpKeepAlive", "true")
        validate()
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    transaction {
        SchemaPreStatements.forEach { exec(it.createStatement().single()) }
        SchemaUtils.createMissingTablesAndColumns(*AllTables)
        SchemaPostStatements.forEach { exec(it.createStatement().single()) }
    }

    val osuService = OsuApiService(
        clientId = AppConfig.osuClientId(),
        clientSecret = AppConfig.osuClientSecret()
    )
    return AppRepositories(
        matchRepository = MatchRepository(),
        gameRepository = GameRepository(),
        scoreRepository = ScoreRepository(),
        eventRepository = EventRepository(),
        playerRepository = PlayerRepository(),
        beatmapsetRepository = BeatmapsetRepository(),
        beatmapRepository = BeatmapRepository(),
        matchParserQueueRepository = MatchParserQueueRepository(),
        matchTitleChangeRepository = MatchTitleChangeRepository(),
        osuService = osuService,
    )
}
