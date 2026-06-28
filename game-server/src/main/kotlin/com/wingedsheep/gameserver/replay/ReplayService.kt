package com.wingedsheep.gameserver.replay

import com.wingedsheep.engine.state.GameState
import org.springframework.stereotype.Component

/**
 * The single entry point for reading replays. Resolves a game id to its [CompactReplay] (in-memory
 * cache first for just-finished games, then the durable [ReplayStore]) and re-derives whatever the
 * caller needs via [ReplayReconstructor]: the full snapshot+delta stream for the viewer, or a single
 * frame's unmasked [GameState] for "share frame as scenario".
 */
@Component
class ReplayService(
    private val cache: GameHistoryRepository,
    private val store: ReplayStore,
    private val reconstructor: ReplayReconstructor,
) {
    /**
     * Record a finished game. It always enters the in-memory cache (immediate public/admin/tournament
     * viewing); it is additionally written to the durable store only when [durable] — the caller
     * passes false for AI-only games, which never surface in any user's history and would otherwise
     * flood the table (mirroring the stats sink's human-seat policy).
     */
    fun save(replay: CompactReplay, durable: Boolean) {
        cache.save(replay)
        if (durable) store.save(replay)
    }

    /** The compact record for [gameId], cache first then durable store, or null if unknown. */
    fun find(gameId: String): CompactReplay? =
        cache.findById(gameId) ?: store.findByGameId(gameId)

    /** Rebuild the snapshot + delta stream the client replay viewer consumes. */
    fun reconstruct(replay: CompactReplay): ReconstructedReplay = reconstructor.reconstruct(replay)

    /** Convenience: resolve [gameId] and reconstruct it in one step. */
    fun reconstructById(gameId: String): ReconstructedReplay? = find(gameId)?.let { reconstruct(it) }

    /** The full unmasked [GameState] at [frame] for [gameId] (0 = initial), or null. */
    fun reconstructStateAt(gameId: String, frame: Int): GameState? =
        find(gameId)?.let { reconstructor.reconstructStateAt(it, frame) }

    /** Recently finished games this player took part in (in-memory cache only). */
    fun recentForPlayer(playerId: String): List<CompactReplay> = cache.findByPlayerId(playerId)

    /** Every recently finished game in the in-memory cache (admin dashboard). */
    fun recentGames(): List<CompactReplay> = cache.findAll()
}
