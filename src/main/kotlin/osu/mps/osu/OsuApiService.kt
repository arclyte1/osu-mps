package osu.mps.osu

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import osu.mps.config.AppConfig
import osu.mps.core.ratelimiter.PriorityRateLimiter
import osu.mps.core.ratelimiter.RequestPriority
import osu.mps.core.ratelimiter.osuPriority
import osu.mps.core.ratelimiter.osuPriorityOrNull
import osu.mps.data.BeatmapDto
import osu.mps.data.BeatmapsetDto
import osu.mps.data.EventDto
import osu.mps.data.GameDto
import osu.mps.data.MatchDto
import osu.mps.data.MatchTitleChangeDto
import osu.mps.data.PlayerDto
import osu.mps.data.ScoreDto
import osu.mps.osu.parser.MatchParser

sealed class GetMatchResult {
    data class Success(
        val match: MatchDto,
        val games: List<GameDto>,
        val scores: List<ScoreDto>,
        val events: List<EventDto>,
        val players: List<PlayerDto>,
        val beatmapsets: List<BeatmapsetDto>,
        val beatmaps: List<BeatmapDto>,
        val matchTitleChanges: List<MatchTitleChangeDto>,
        val lastParsedEventId: Long,
        val isReachedEnd: Boolean,
    ) : GetMatchResult()

    data object Private : GetMatchResult()

    data object NotFound : GetMatchResult()

    data class Failure(
        val reason: String,
        val rawJson: String?,
    ) : GetMatchResult()
}

class OsuApiService(
    private val clientId: String,
    private val clientSecret: String,
    private val rateLimiter: PriorityRateLimiter = PriorityRateLimiter(60_000L / AppConfig.osuApiRateLimit()),
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = createOsuHttpClient()

    private val authorizedHttpClient = createOsuHttpClient {
        defaultRequest { url(API_V2_BASE) }
    }.also { client ->
        client.plugin(HttpSend).intercept { request ->
            request.headers.append(HttpHeaders.Authorization, "Bearer ${getAccessToken()}")
            execute(request)
        }
    }

    private fun createOsuHttpClient(
        configure: HttpClientConfig<CIOEngineConfig>.() -> Unit = {}
    ): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(this@OsuApiService.json)
        }
        configure()
    }.also { client ->
        client.plugin(HttpSend).intercept { request ->
            val priority = request.osuPriorityOrNull() ?: RequestPriority.Normal
            rateLimiter.acquire(priority)
            execute(request)
        }
    }

    suspend fun getLastMatchId(): Int? {
        val response = authorizedHttpClient.get("matches") {
            osuPriority(RequestPriority.High)
            parameter("limit", 1)
        }

        if (response.status.value !in 200..299) {
            logger.error("Failed to get last match id")
            return null
        }

        runCatching {
            json.parseToJsonElement(response.bodyAsText())
                .jsonObject["matches"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("id")
                ?.jsonPrimitive?.intOrNull
        }.onSuccess {
            return it
        }.onFailure {
            logger.error("Failed to parse last match id: $it")
            return null
        }
        return null
    }

    suspend fun getMatch(id: Int, afterEventId: Long, lastParsedMatchTitle: String?): GetMatchResult {
        val response = authorizedHttpClient.get("matches/${id}") {
            parameter("limit", 100)
            parameter("after", afterEventId)
        }
        val body = response.bodyAsText()

        if (response.status.value == 401) {
            val lastMatchId = getLastMatchId()
            return if (lastMatchId != null) {
                GetMatchResult.Private
            } else {
                GetMatchResult.Failure(response.status.description, body)
            }
        }

        if (response.status.value == 404) {
            val lastMatchId = getLastMatchId()
            return if (lastMatchId != null) {
                GetMatchResult.NotFound
            } else {
                GetMatchResult.Failure(response.status.description, body)
            }
        }

        if (response.status.value !in 200..299) {
            return GetMatchResult.Failure(response.status.description, body)
        }

        val validation = GetMatchResponseSchemaValidator.validate(body)
        if (!validation.isValid || validation.warnings.isNotEmpty()) {
            val details = (validation.errors + validation.warnings).joinToString("; ") { "${it.path}: ${it.message}" }
            return GetMatchResult.Failure("Schema validation failed: $details", body)
        }

        return MatchParser(body, lastParsedMatchTitle).parse()
    }

    private suspend fun getAccessToken(): String {
        val existing = cachedToken
        if (existing != null && !existing.isExpired()) return existing.value

        return tokenLock.withLock {
            val current = cachedToken
            if (current != null && !current.isExpired()) return@withLock current.value

            val tokenResponse = httpClient.post(OAUTH_TOKEN_URL) {
                osuPriority(RequestPriority.High)
                contentType(ContentType.Application.Json)
                setBody(
                    mapOf(
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "grant_type" to "client_credentials",
                        "scope" to "public"
                    )
                )
            }.body<TokenResponse>()

            val token = Token(
                value = tokenResponse.accessToken,
                expiresAtEpochMs = System.currentTimeMillis() +
                    ((tokenResponse.expiresIn - 120).coerceAtLeast(30) * 1000)
            )
            cachedToken = token
            token.value
        }
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long
    )

    private data class Token(
        val value: String,
        val expiresAtEpochMs: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtEpochMs
    }

    private val tokenLock = Mutex()
    private var cachedToken: Token? = null

    companion object {
        private const val API_V2_BASE = "https://osu.ppy.sh/api/v2/"
        private const val OAUTH_TOKEN_URL = "https://osu.ppy.sh/oauth/token"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val SOCKET_TIMEOUT_MS = 30_000L
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }
}
