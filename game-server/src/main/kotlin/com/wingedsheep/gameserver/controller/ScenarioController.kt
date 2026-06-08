package com.wingedsheep.gameserver.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gameserver.ai.AiGameManager
import com.wingedsheep.gameserver.scenario.ScenarioBuilderService
import com.wingedsheep.gameserver.scenario.ScenarioMode
import com.wingedsheep.gameserver.scenario.ScenarioRequest
import com.wingedsheep.gameserver.scenario.ScenarioResponse
import com.wingedsheep.gameserver.scenario.ScenarioSessionFactory
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap

/**
 * Player-facing endpoint for the Scenario Builder / Tester. Unlike
 * [DevScenarioController] this is **not** gated behind `game.dev-endpoints.enabled`: any
 * player can construct an arbitrary board state and play it (against themselves via hotseat,
 * against the AI, or two-player).
 *
 * Abuse is bounded by request validation (unknown cards rejected, per-zone + total card caps)
 * and a light per-IP rate limit. Sessions are in-memory only and reaped on game-over.
 */
@RestController
@RequestMapping("/api/scenarios")
class ScenarioController(
    private val cardRegistry: CardRegistry,
    private val aiGameManager: AiGameManager,
    private val scenarioBuilderService: ScenarioBuilderService,
    private val scenarioSessionFactory: ScenarioSessionFactory,
) {
    private val logger = LoggerFactory.getLogger(ScenarioController::class.java)

    /** Sliding-window per-IP limiter: at most [MAX_PER_WINDOW] creations per [WINDOW_MS]. */
    private val recentByIp = ConcurrentHashMap<String, MutableList<Long>>()

    @PostMapping
    fun createScenario(
        @RequestBody request: ScenarioRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        val ip = httpRequest.remoteAddr ?: "unknown"
        if (isRateLimited(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(errorBody(listOf("Too many scenarios created — please wait a moment and try again.")))
        }

        // AI-seat preconditions (server-config concerns, not request validity).
        if (request.aiPlayer != null && request.aiPlayer !in setOf(1, 2)) {
            return ResponseEntity.badRequest().body(errorBody(listOf("aiPlayer must be 1 or 2 (got ${request.aiPlayer})")))
        }
        if (request.effectiveMode == ScenarioMode.AI && !aiGameManager.aiEnabledToggle) {
            return ResponseEntity.badRequest()
                .body(errorBody(listOf("AI is not enabled on this server.")))
        }

        val errors = scenarioBuilderService.validate(request, enforceLimits = true)
        if (errors.isNotEmpty()) {
            return ResponseEntity.badRequest().body(errorBody(errors))
        }

        return try {
            val build = scenarioBuilderService.buildScenario(request)
            // Production: random per-seat tokens, no localhost open-URLs in the message.
            val response: ScenarioResponse = scenarioSessionFactory.createSession(
                build = build,
                request = request,
                player1Token = null,
                player2Token = null,
                includeDevUrls = false,
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Failed to create scenario", e)
            ResponseEntity.badRequest().body(errorBody(listOf("Failed to create scenario: ${e.message}")))
        }
    }

    /** Card names available for the scenario builder's picker. */
    @GetMapping("/cards")
    fun listCards(): ResponseEntity<List<String>> =
        ResponseEntity.ok(cardRegistry.allCardNames().sorted())

    private fun errorBody(messages: List<String>): Map<String, Any> =
        mapOf("errors" to messages)

    private fun isRateLimited(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val window = recentByIp.computeIfAbsent(ip) { mutableListOf() }
        synchronized(window) {
            window.removeAll { now - it > WINDOW_MS }
            if (window.size >= MAX_PER_WINDOW) return true
            window.add(now)
        }
        return false
    }

    companion object {
        private const val WINDOW_MS = 60_000L
        private const val MAX_PER_WINDOW = 20
    }
}
