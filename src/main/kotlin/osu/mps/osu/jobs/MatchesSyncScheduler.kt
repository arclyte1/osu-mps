package osu.mps.osu.jobs

import io.ktor.util.collections.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import osu.mps.config.AppConfig
import osu.mps.core.scheduler.SyncScheduler
import osu.mps.data.MatchParserQueueDto
import osu.mps.data.MatchParserQueueStatus
import osu.mps.osu.GetMatchResult
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
import java.time.Instant
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

class MatchesSyncScheduler(
    private val matchRepository: MatchRepository,
    private val gameRepository: GameRepository,
    private val scoreRepository: ScoreRepository,
    private val eventRepository: EventRepository,
    private val playerRepository: PlayerRepository,
    private val beatmapsetRepository: BeatmapsetRepository,
    private val beatmapRepository: BeatmapRepository,
    private val matchParserQueueRepository: MatchParserQueueRepository,
    private val matchTitleChangeRepository: MatchTitleChangeRepository,
    private val osuService: OsuApiService,
) : SyncScheduler(1.minutes) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val matchIdsInDownloadQueue = ConcurrentSet<Int>()

    override suspend fun runOnce() {
        val maxMatchIdInDb = maxOf(
            matchRepository.maxId() ?: 0,
            matchParserQueueRepository.maxId() ?: 0,
        )
        val matchNewDownloadStartId = if (maxMatchIdInDb > 0) maxMatchIdInDb + 1 else AppConfig.startMatchId()
        val lastMatchId = AppConfig.endMatchId() ?: osuService.getLastMatchId()

        val matchDownloadTasksInQueue = matchIdsInDownloadQueue.size
        val newTasksMaxCount = MAX_TASKS_IN_QUEUE - matchDownloadTasksInQueue

        val mpsToDownload = emptySet<LoadMatchTask>().toMutableSet()
        val checkAgainMps = matchParserQueueRepository.selectOldestChecked(newTasksMaxCount)
            .filter { Instant.parse(it.lastChecked).isBefore(Instant.now().minusSeconds(DOWNLOAD_SAME_MATCH_DELAY_SECONDS)) }
            .filter { it.matchId !in matchIdsInDownloadQueue }

        when {
            lastMatchId == null || lastMatchId < matchNewDownloadStartId -> {
                mpsToDownload.addAll(checkAgainMps.map(LoadMatchTask::Update))
            }
            lastMatchId - maxMatchIdInDb > MAX_TASKS_IN_QUEUE / 2 -> {
                mpsToDownload.addAll(checkAgainMps.take(newTasksMaxCount / 2).map(LoadMatchTask::Update))
                val added = mpsToDownload.size
                val matchNewDownloadEndId = min(lastMatchId, matchNewDownloadStartId + newTasksMaxCount - added)
                mpsToDownload.addAll((matchNewDownloadStartId..matchNewDownloadEndId).map(LoadMatchTask::New))
            }
            else -> {
                val matchNewDownloadEndId = min(lastMatchId, matchNewDownloadStartId + newTasksMaxCount)
                mpsToDownload.addAll((matchNewDownloadStartId..matchNewDownloadEndId).map(LoadMatchTask::New))
                val added = mpsToDownload.size
                mpsToDownload.addAll(checkAgainMps.take(newTasksMaxCount - added).map(LoadMatchTask::Update))
            }
        }

        // Insert new tasks to queue
        val newTasks = mpsToDownload.filterIsInstance<LoadMatchTask.New>()
        matchParserQueueRepository.upsert(*newTasks.map { MatchParserQueueDto(
            matchId = it.matchId,
            lastChecked = Instant.now().toString(),
            matchStatus = MatchParserQueueStatus.UNCHECKED,
            lastParsedEventId = 0
        ) }.toTypedArray())

        // Download
        mpsToDownload.forEach(::createLoadMatchTask)
    }

    private fun createLoadMatchTask(task: LoadMatchTask) = scope.launch {
        matchIdsInDownloadQueue.add(task.matchId)
        try {
            val lastParsedEvent = (task as? LoadMatchTask.Update)?.dto?.lastParsedEventId ?: 0L
            val lastParsedMatchTitle = if (task is LoadMatchTask.Update) {
                matchTitleChangeRepository.getLastMatchTitleChange(task.matchId)?.title
            } else null
            val matchResult = osuService.getMatch(task.matchId, lastParsedEvent, lastParsedMatchTitle)

            val queueDto = when(task) {
                is LoadMatchTask.New -> MatchParserQueueDto(
                    matchId = task.matchId,
                    lastChecked = Instant.now().toString(),
                    matchStatus = MatchParserQueueStatus.UNCHECKED,
                    lastParsedEventId = 0,
                )
                is LoadMatchTask.Update -> task.dto.copy(lastChecked = Instant.now().toString())
            }

            when (matchResult) {
                is GetMatchResult.Failure -> {
                    logger.error(matchResult.reason)
                    val updatedQueueDto = queueDto.copy(
                        matchStatus = MatchParserQueueStatus.ERROR,
                        errorText = matchResult.reason,
                        rawJson = matchResult.rawJson,
                    )
                    matchParserQueueRepository.upsert(updatedQueueDto)
                }
                is GetMatchResult.Success -> {
                    matchRepository.upsert(matchResult.match)
                    eventRepository.upsert(*matchResult.events.toTypedArray())
                    gameRepository.upsert(*matchResult.games.toTypedArray())
                    scoreRepository.upsert(*matchResult.scores.toTypedArray())
                    beatmapsetRepository.upsert(*matchResult.beatmapsets.toTypedArray())
                    beatmapRepository.upsert(*matchResult.beatmaps.toTypedArray())
                    playerRepository.upsert(*matchResult.players.toTypedArray())
                    matchTitleChangeRepository.upsert(*matchResult.matchTitleChanges.toTypedArray())

                    if (matchResult.isReachedEnd) {
                        matchParserQueueRepository.remove(task.matchId)
                    } else {
                        val updatedQueueDto = queueDto.copy(
                            matchStatus = MatchParserQueueStatus.ONGOING,
                            lastParsedEventId = matchResult.lastParsedEventId,
                            errorText = null,
                            rawJson = null,
                        )
                        matchParserQueueRepository.upsert(updatedQueueDto)
                    }
                }
                GetMatchResult.Private, GetMatchResult.NotFound -> {
                    matchParserQueueRepository.remove(task.matchId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to load match $task", e)
        } finally {
            matchIdsInDownloadQueue.remove(task.matchId)
        }
    }

    private sealed class LoadMatchTask(open val matchId: Int) {
        data class New(override val matchId: Int) : LoadMatchTask(matchId)
        data class Update(val dto: MatchParserQueueDto) : LoadMatchTask(dto.matchId)
    }

    companion object {
        val MAX_TASKS_IN_QUEUE = (AppConfig.osuApiRateLimit() * 1.5).toInt()
        const val DOWNLOAD_SAME_MATCH_DELAY_SECONDS = 300L
    }
}
