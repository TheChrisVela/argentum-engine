package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.PermanentTypesEnteredBattlefieldThisTurnComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.model.EntityId

/**
 * Records the entry of a permanent for per-player, per-turn "an X entered the battlefield
 * under your control this turn" tracking.
 *
 * Every code path that puts a permanent onto the battlefield should call [record] with the
 * controller and the permanent's base card types:
 *  - [ZoneTransitionService.moveToZone] handles cards moving in via spell resolution,
 *    reanimation, blink, exile-return, etc.
 *  - Each token-creation executor (CreateTokenExecutor, CreatePredefinedTokenExecutor, the
 *    copy-token executors, CreateRoleTokenExecutor, etc.) places its token directly into the
 *    battlefield zone and must call this helper for its tokens to count.
 *
 * The record uses the entering card's **base** [CardType]s. That matches the gameplay rule:
 * what was true at the moment of entry stays true for the rest of the turn (the ruling for
 * Mechan Shieldmate explicitly calls out "It doesn't matter if that artifact stays an artifact
 * or stays under your control"). Continuous effects that subsequently change the permanent's
 * type don't retroactively rewrite this record.
 *
 * Cleared at end of turn by [com.wingedsheep.engine.core.CleanupPhaseManager].
 */
object PermanentEntryTracker {

    /**
     * Record that a permanent with the given [cardTypes] entered the battlefield under
     * [controllerId]. Idempotent — recording the same type twice has no effect.
     */
    fun record(state: GameState, controllerId: EntityId, cardTypes: Set<CardType>): GameState {
        if (cardTypes.isEmpty()) return state
        return state.updateEntity(controllerId) { container ->
            val existing = container.get<PermanentTypesEnteredBattlefieldThisTurnComponent>()
                ?: PermanentTypesEnteredBattlefieldThisTurnComponent()
            val merged = existing.cardTypes + cardTypes
            if (merged == existing.cardTypes) return@updateEntity container
            container.with(PermanentTypesEnteredBattlefieldThisTurnComponent(merged))
        }
    }

    /**
     * Convenience: record using the base [CardComponent.typeLine.cardTypes] of the entity.
     */
    fun recordFromCard(state: GameState, controllerId: EntityId, cardComponent: CardComponent): GameState =
        record(state, controllerId, cardComponent.typeLine.cardTypes)
}
