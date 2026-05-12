package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.MayPlayFromExileComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * An effect primitive that grants the controller permission to cast exactly one chosen
 * card from a named exile set until the cleanup step of the controller's next turn.
 *
 * The card is identified by [chosen]; all other cards in the [from] collection receive
 * no play permission. This is the "grant duration-bounded play permission scoped to a
 * single exile-set entry" step of the Heroes' Hangout-style pattern:
 *   gather → mark-chosen → **grant permission** → expire on end-of-controller's-next-turn
 *
 * @param from    Name of the pipeline collection holding the exiled card set (unused at
 *                execution time — the selection is already resolved to [chosen]).
 * @param chosen  The entity ID of the card the player chose from the exile set.
 */
data class ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurnEffect(
    val from: String,
    val chosen: EntityId
)

/**
 * Executor for [ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurnEffect].
 *
 * Attaches [MayPlayFromExileComponent] to the chosen card only. The permission is
 * scoped to the effect controller and expires at the cleanup of their next turn.
 */
class ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurnExecutor {

    fun execute(
        state: GameState,
        effect: ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurnEffect,
        context: EffectContext
    ): EffectResult {
        val controllerId = context.controllerId
        val expiresAfterTurn = resolveEndOfNextControllerTurn(state, controllerId)

        val newState = state.updateEntity(effect.chosen) { container ->
            container.with(
                MayPlayFromExileComponent(
                    controllerId     = controllerId,
                    expiresAfterTurn = expiresAfterTurn,
                    permanent        = false
                )
            )
        }

        return EffectResult.success(newState)
    }

    /**
     * Returns the turn number whose cleanup will remove the permission.
     *
     * "Until end of your next turn" with [includeCurrentTurn = false]: always extends to the
     * controller's next turn, even when it is already the controller's turn. For a two-player
     * game on the controller's own turn this is [turnNumber + 2].
     */
    private fun resolveEndOfNextControllerTurn(state: GameState, controllerId: EntityId): Int {
        val turnOrder   = state.turnOrder
        val playerCount = if (turnOrder.isEmpty()) 2 else turnOrder.size
        val playerIndex = turnOrder.indexOf(controllerId)
        val activeIndex = turnOrder.indexOf(state.activePlayerId)

        val turnsUntilNext = if (playerIndex == activeIndex && playerIndex >= 0) {
            playerCount
        } else {
            val raw = playerIndex - activeIndex
            if (raw > 0) raw else raw + playerCount
        }

        return state.turnNumber + turnsUntilNext
    }
}
