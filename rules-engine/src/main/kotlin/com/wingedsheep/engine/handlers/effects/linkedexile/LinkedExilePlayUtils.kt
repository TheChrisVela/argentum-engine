package com.wingedsheep.engine.handlers.effects.linkedexile

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GrantMayCastFromLinkedExile
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/**
 * Shared logic for *playing lands* exiled with a permanent that has a
 * [GrantMayCastFromLinkedExile] static ability (e.g. Valgavoth, Terror Eater —
 * "During your turn, you may play cards exiled with Valgavoth").
 *
 * Casting *spells* from linked exile is handled by `CastFromZoneEnumerator.enumerateLinkedExile`
 * (which deliberately skips lands — most linked-exile granters say "cast spells"). Lands are a
 * separate play path: this helper lets `PlayLandEnumerator` surface them and `PlayLandHandler`
 * authorize them, but only for granters whose `filter` actually admits land cards.
 */
object LinkedExilePlayUtils {

    /** A linked-exile grant that currently lets [playerId] *play lands* from its pile. */
    data class LandGranter(val sourceId: EntityId, val ability: GrantMayCastFromLinkedExile, val exiledIds: List<EntityId>)

    /** All linked-exile grants controlled by [playerId] that admit land cards and are active now. */
    fun landGranters(state: GameState, playerId: EntityId, cardRegistry: CardRegistry): List<LandGranter> {
        val result = mutableListOf<LandGranter>()
        for (entityId in state.getBattlefield()) {
            val container = state.getEntity(entityId) ?: continue
            if (container.get<ControllerComponent>()?.playerId != playerId) continue
            val linked = container.get<LinkedExileComponent>() ?: continue
            val cardDef = container.get<CardComponent>()?.let { cardRegistry.getCard(it.cardDefinitionId) } ?: continue
            val grant = cardDef.script.staticAbilities.filterIsInstance<GrantMayCastFromLinkedExile>().firstOrNull() ?: continue
            // The grant must admit lands (filter has no IsNonland predicate) — Valgavoth uses Any.
            if (grant.filter.cardPredicates.any { it is CardPredicate.IsNonland }) continue
            // Timing — "during your turn" grants only let you play lands on your own turn.
            if (grant.duringYourTurnOnly && !state.isActiveTurnFor(playerId)) continue
            result.add(LandGranter(entityId, grant, linked.exiledIds))
        }
        return result
    }

    /** True if [landCardId] is a land currently in exile that [playerId] may play via a linked-exile grant. */
    fun canPlayLand(state: GameState, playerId: EntityId, landCardId: EntityId, cardRegistry: CardRegistry): Boolean {
        val card = state.getEntity(landCardId)?.get<CardComponent>() ?: return false
        if (!card.typeLine.isLand) return false
        val inExile = state.turnOrder.any { pid -> landCardId in state.getZone(ZoneKey(pid, Zone.EXILE)) }
        if (!inExile) return false
        return landGranters(state, playerId, cardRegistry).any { granter ->
            landCardId in granter.exiledIds &&
                (!granter.ability.ownedByYou || card.ownerId == playerId)
        }
    }
}
