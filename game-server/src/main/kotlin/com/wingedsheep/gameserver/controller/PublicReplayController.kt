package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.handler.MessageSender
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.persistence.persistenceJson
import com.wingedsheep.gameserver.replay.ReplayService
import com.wingedsheep.gameserver.replay.SpectatorReplayDelta
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Public (unauthenticated) REST controller for viewing game replays via shareable links.
 * Anyone with the game ID can view the replay — replays only contain spectator-view data
 * (no hidden information like hands). The compact replay is re-simulated on demand into the
 * snapshot+delta stream the client expects; the unguessable game id is the share token.
 */
@RestController
@RequestMapping("/api/public/replays")
class PublicReplayController(
    private val replayService: ReplayService,
    private val messageSender: MessageSender
) {

    @GetMapping("/{gameId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getReplay(@PathVariable gameId: String): ResponseEntity<Any> {
        val replay = replayService.find(gameId)
            ?: return ResponseEntity.notFound().build()
        val reconstructed = replayService.reconstruct(replay)

        val initialSnapshotJson = messageSender.json.encodeToString(
            ServerMessage.SpectatorStateUpdate.serializer(),
            reconstructed.initialSnapshot
        )
        val deltasJson = messageSender.json.encodeToString(
            ListSerializer(SpectatorReplayDelta.serializer()),
            reconstructed.deltas
        )

        val response = PublicReplayResponse(
            gameId = replay.gameId,
            player1Name = replay.players.getOrNull(0)?.name ?: "",
            player2Name = replay.players.getOrNull(1)?.name ?: "",
            winnerName = replay.winnerName,
            startedAt = replay.startedAt,
            endedAt = replay.endedAt,
            snapshotCount = reconstructed.frameCount
        )

        // Build combined JSON with metadata + initial snapshot + deltas
        // We manually compose because snapshots use kotlinx.serialization
        val metadataJson = messageSender.json.encodeToString(response)
        val combinedJson = """{"metadata":$metadataJson,"initialSnapshot":$initialSnapshotJson,"deltas":$deltasJson}"""

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(combinedJson)
    }

    /**
     * The full, unmasked game state for a single replay frame, used by "share frame as
     * scenario" to reproduce the EXACT position (stack, targets, floating effects, mana, …).
     * Re-simulated from the compact record up to [frame]. Served separately from [getReplay] so
     * normal (masked) replay viewing never receives hidden information — only an explicit share
     * does. The game is finished, so revealing the full state of a snapshot is intended.
     */
    @GetMapping("/{gameId}/frames/{frame}/full-state", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getFrameFullState(
        @PathVariable gameId: String,
        @PathVariable frame: Int,
    ): ResponseEntity<String> {
        val state = replayService.reconstructStateAt(gameId, frame)
            ?: return ResponseEntity.notFound().build()
        // persistenceJson has allowStructuredMapKeys (GameState.zones is keyed by ZoneKey).
        val json = persistenceJson.encodeToString(GameState.serializer(), state)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json)
    }
}

@Serializable
data class PublicReplayResponse(
    val gameId: String,
    val player1Name: String,
    val player2Name: String,
    val winnerName: String?,
    val startedAt: String,
    val endedAt: String,
    val snapshotCount: Int
)
