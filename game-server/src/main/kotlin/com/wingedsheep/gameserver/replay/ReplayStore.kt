package com.wingedsheep.gameserver.replay

import com.wingedsheep.gameserver.persistence.GameReplayRepository
import com.wingedsheep.gameserver.persistence.GameReplayRow
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Durable storage for [CompactReplay]s, so a signed-in player can revisit and share games long after
 * they scroll out of the in-memory [GameHistoryRepository] cache (and across server restarts). Which
 * implementation is wired depends on whether accounts are enabled, so the game-over path stays
 * decoupled from the persistence layer — exactly like [com.wingedsheep.gameserver.stats.MatchResultSink].
 */
interface ReplayStore {
    fun save(replay: CompactReplay)
    fun findByGameId(gameId: String): CompactReplay?
}

/** Default: accounts disabled — replays live only in the in-memory cache. */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "false", matchIfMissing = true)
class NoOpReplayStore : ReplayStore {
    override fun save(replay: CompactReplay) = Unit
    override fun findByGameId(gameId: String): CompactReplay? = null
}

/** Accounts enabled: persist the compact replay to Postgres (upsert by game id). */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class JdbcReplayStore(private val replays: GameReplayRepository) : ReplayStore {
    private val logger = LoggerFactory.getLogger(JdbcReplayStore::class.java)

    override fun save(replay: CompactReplay) {
        val existingId = replays.findByGameId(replay.gameId)?.id
        replays.save(
            GameReplayRow(
                id = existingId,
                gameId = replay.gameId,
                format = replay.setup.format::class.simpleName,
                winnerName = replay.winnerName,
                tournamentName = replay.tournamentName,
                startedAt = parseInstant(replay.startedAt),
                endedAt = parseInstant(replay.endedAt) ?: Instant.now(),
                frameCount = replay.frameCount,
                playerNames = replay.players.joinToString(", ") { it.name },
                data = ReplayCodec.encode(replay),
            )
        )
        logger.debug("Persisted compact replay {} ({} actions)", replay.gameId, replay.actions.size)
    }

    override fun findByGameId(gameId: String): CompactReplay? =
        replays.findByGameId(gameId)?.let { ReplayCodec.decode(it.data) }

    private fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
