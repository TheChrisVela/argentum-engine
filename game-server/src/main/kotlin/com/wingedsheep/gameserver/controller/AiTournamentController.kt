package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.handler.LobbyHandler
import com.wingedsheep.engine.limited.BoosterGenerator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Development-only endpoint to create a sealed tournament with AI-only players.
 *
 * After creation, open /tournament/{lobbyId} in the browser to spectate.
 * AI players will build decks, start matches, and play autonomously.
 *
 * Enable with: game.dev-endpoints.enabled=true
 */
@RestController
@RequestMapping("/api/dev/ai-tournament")
@ConditionalOnProperty(name = ["game.dev-endpoints.enabled"], havingValue = "true")
class AiTournamentController(
    private val lobbyHandler: LobbyHandler,
    private val boosterGenerator: BoosterGenerator
) {
    private val logger = LoggerFactory.getLogger(AiTournamentController::class.java)

    data class AiTournamentRequest(
        val setCodes: List<String>? = null,
        val playerCount: Int? = null,
        /** Optional per-player model overrides. Index 0 = player 1, index 1 = player 2, etc.
         *  Falls back to the server's configured model for any unspecified slots. */
        val models: List<String>? = null,
        /** Skip LLM deck building and use the fast heuristic builder instead. */
        val heuristicDeckbuilding: Boolean? = null,
        /**
         * Optional pre-built decks (one per player, indexed by slot) as cardName→count maps.
         * When provided, the lobby is created in PREMADE_DECKS format and AI deckbuilding is
         * skipped entirely — boosters are not generated and `setCodes` is ignored.
         */
        val decks: List<Map<String, Int>>? = null
    )

    data class AiTournamentResponse(
        val lobbyId: String,
        val spectateUrl: String,
        val message: String
    )

    @PostMapping
    fun createAiTournament(
        @RequestBody request: AiTournamentRequest?
    ): ResponseEntity<AiTournamentResponse> {
        val decks = request?.decks?.takeIf { it.isNotEmpty() }
        val playerCount = decks?.size
            ?: request?.playerCount?.coerceIn(2, 8) ?: 2

        return try {
            val lobbyId = if (decks != null) {
                if (decks.size < 2) {
                    return ResponseEntity.badRequest().body(AiTournamentResponse(
                        lobbyId = "", spectateUrl = "",
                        message = "At least 2 decks are required for a fixed-deck AI tournament"
                    ))
                }
                lobbyHandler.createAiTournamentWithFixedDecks(decks, request.models)
            } else {
                // Auto-pick a random *fully implemented* set (partial sets aren't reliable enough
                // for an unattended AI tournament); fall back to any set if none qualify.
                val setCodes = request?.setCodes?.ifEmpty { null }
                    ?: boosterGenerator.availableSets.values
                        .filter { it.fullyImplemented }
                        .map { it.setCode }
                        .ifEmpty { boosterGenerator.availableSets.keys.toList() }
                        .let { listOf(it.random()) }
                lobbyHandler.createAiTournament(setCodes, playerCount, request?.models, request?.heuristicDeckbuilding)
            }

            val mode = if (decks != null) "fixed-decks" else "sealed (sets=${request?.setCodes ?: "auto"})"
            logger.info("AI tournament created via REST: lobbyId=$lobbyId, mode=$mode, players=$playerCount")

            ResponseEntity.ok(AiTournamentResponse(
                lobbyId = lobbyId,
                spectateUrl = "/tournament/$lobbyId",
                message = "AI tournament created. Open /tournament/$lobbyId to spectate. " +
                    if (decks != null) "Players will start playing shortly with the supplied decks."
                    else "AI players are building decks and will start playing shortly."
            ))
        } catch (e: Exception) {
            logger.error("Failed to create AI tournament: ${e.message}", e)
            ResponseEntity.badRequest().body(AiTournamentResponse(
                lobbyId = "",
                spectateUrl = "",
                message = "Failed to create AI tournament: ${e.message}"
            ))
        }
    }

    @GetMapping("/sets")
    fun listAvailableSets(): ResponseEntity<List<SetInfo>> {
        val sets = boosterGenerator.availableSets.map { (code, config) ->
            SetInfo(code = code, name = config.setName)
        }.sortedBy { it.name }
        return ResponseEntity.ok(sets)
    }

    data class SetInfo(val code: String, val name: String)
}
