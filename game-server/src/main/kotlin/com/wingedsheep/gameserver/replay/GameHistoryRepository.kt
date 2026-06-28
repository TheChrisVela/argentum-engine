package com.wingedsheep.gameserver.replay

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * In-memory cache of recently completed game replays, stored in the [CompactReplay] form (a few
 * KB each — just the setup and the input stream). Capped at 100 games (oldest evicted first).
 *
 * This is the hot cache for just-finished games (so a viewer who clicks "replay" right after a game
 * gets an instant hit). Durable storage lives in [ReplayStore] (Postgres); [ReplayService] checks
 * this first, then falls back to the store.
 */
@Component
class GameHistoryRepository {

    private val history = ConcurrentLinkedDeque<CompactReplay>()
    private val maxSize = 100

    fun save(replay: CompactReplay) {
        // De-dupe by gameId so a re-saved game doesn't appear twice.
        history.removeIf { it.gameId == replay.gameId }
        history.addFirst(replay)
        while (history.size > maxSize) {
            history.removeLast()
        }
    }

    fun findAll(): List<CompactReplay> = history.toList()

    fun findById(gameId: String): CompactReplay? =
        history.find { it.gameId == gameId }

    fun findByPlayerId(playerId: String): List<CompactReplay> =
        history.filter { replay -> replay.players.any { it.playerId == playerId } }
}
