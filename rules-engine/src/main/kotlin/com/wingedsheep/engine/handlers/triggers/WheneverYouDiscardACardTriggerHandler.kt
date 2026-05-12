package com.wingedsheep.engine.handlers.triggers

import com.wingedsheep.engine.core.CardsDiscardedEvent
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerAbilityResolver
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.GameEvent as SdkGameEvent
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Detects 'whenever you discard a card' triggered abilities.
 *
 * Listens for [CardsDiscardedEvent] (hand-to-graveyard discard) and enqueues a
 * [PendingTrigger] for every battlefield permanent whose trigger spec is
 * [SdkGameEvent.DiscardEvent] and whose player predicate matches the discarding player.
 */
class WheneverYouDiscardACardTriggerHandler(
    private val abilityResolver: TriggerAbilityResolver
) {

    fun detectDiscardTriggers(
        state: GameState,
        event: CardsDiscardedEvent,
        triggers: MutableList<PendingTrigger>
    ) {
        val discardingPlayerId = event.playerId
        val projected = state.projectedState

        for (permanentId in state.getBattlefield()) {
            val container = state.getEntity(permanentId) ?: continue
            val cardComponent = container.get<CardComponent>() ?: continue
            if (container.has<FaceDownComponent>()) continue
            val controllerId = projected.getController(permanentId) ?: continue

            val abilities = abilityResolver.getTriggeredAbilities(
                permanentId, cardComponent.cardDefinitionId, state
            )

            for (ability in abilities) {
                if (ability.activeZone != Zone.BATTLEFIELD) continue
                val trigger = ability.trigger as? SdkGameEvent.DiscardEvent ?: continue

                val playerMatches = when (trigger.player) {
                    Player.You -> discardingPlayerId == controllerId
                    Player.Opponent -> discardingPlayerId != controllerId
                    else -> true
                }
                if (!playerMatches) continue

                triggers.add(
                    PendingTrigger(
                        ability = ability,
                        sourceId = permanentId,
                        sourceName = cardComponent.name,
                        controllerId = controllerId,
                        triggerContext = TriggerContext(triggeringPlayerId = discardingPlayerId)
                    )
                )
            }
        }
    }
}
