package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent as EngineGameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.LibraryPlacement
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CascadeEffect
import kotlin.reflect.KClass

/**
 * Executor for [CascadeEffect] (CR 702.85).
 *
 * The cascade trigger is built so that its `triggeringEntityId` is the spell
 * being cast — i.e. the spell whose cascade is resolving (or the spell that was
 * granted cascade by another effect such as Wildsear, Scouring Maw). The
 * executor reads that spell's mana value to derive the threshold, then walks
 * the controller's library top-down, exiling each card until either:
 *   - a nonland card with mana value strictly less than the threshold is
 *     exiled (the "cascade card"), or
 *   - the library is exhausted.
 *
 * On a hit, the controller may cast the cascade card for free until end of
 * turn. All other cards exiled by this cascade are then put on the bottom of
 * the controller's library in a random order. If no qualifying card is found,
 * every exiled card is put on the bottom in a random order and no spell is
 * granted free cast.
 *
 * Limitation: if the controller chooses not to cast the cascade card, it
 * lingers in exile after the permission expires rather than being moved to
 * the bottom of the library. This mirrors the existing
 * `shuffleAndExileTopPlayFree` / Mind's Desire flow and is consistent with the
 * codebase's may-play-from-exile pattern.
 */
class CascadeExecutor : EffectExecutor<CascadeEffect> {

    override val effectType: KClass<CascadeEffect> = CascadeEffect::class

    override fun execute(
        state: GameState,
        effect: CascadeEffect,
        context: EffectContext
    ): EffectResult {
        val controllerId = context.controllerId
        val triggeringSpellId = context.triggeringEntityId
            ?: return EffectResult.success(state)

        val threshold = state.getEntity(triggeringSpellId)
            ?.get<CardComponent>()?.manaValue
            ?: return EffectResult.success(state)

        var currentState = state
        val allEvents = mutableListOf<EngineGameEvent>()
        val exiledCards = mutableListOf<EntityId>()
        var cascadeCard: EntityId? = null

        val library = currentState.getZone(ZoneKey(controllerId, Zone.LIBRARY))
        for (cardId in library) {
            exiledCards.add(cardId)
            val card = currentState.getEntity(cardId)?.get<CardComponent>()
            if (card != null && !card.typeLine.isLand && card.manaValue < threshold) {
                cascadeCard = cardId
                break
            }
        }

        if (exiledCards.isEmpty()) {
            return EffectResult.success(currentState)
        }

        val sourceName = context.sourceId?.let {
            currentState.getEntity(it)?.get<CardComponent>()?.name
        }
        val cardNames = exiledCards.map { id ->
            currentState.getEntity(id)?.get<CardComponent>()?.name ?: "Unknown"
        }
        val imageUris = exiledCards.map { id ->
            currentState.getEntity(id)?.get<CardComponent>()?.imageUri
        }
        allEvents.add(
            CardsRevealedEvent(
                revealingPlayerId = controllerId,
                cardIds = exiledCards.toList(),
                cardNames = cardNames,
                imageUris = imageUris,
                source = sourceName
            )
        )

        for (cardId in exiledCards) {
            val result = ZoneMovementUtils.moveCardToZone(currentState, cardId, Zone.EXILE)
            if (result.isSuccess) {
                currentState = result.state
                allEvents.addAll(result.events)
            }
        }

        if (cascadeCard != null) {
            currentState = currentState.updateEntity(cascadeCard) { container ->
                container.with(PlayWithoutPayingCostComponent(controllerId = controllerId))
            }
            currentState = currentState.addMayPlayPermission(
                MayPlayPermission(
                    id = EntityId.generate(),
                    cardIds = setOf(cascadeCard),
                    controllerId = controllerId,
                    sourceId = context.sourceId,
                    timestamp = currentState.timestamp,
                )
            )
        }

        val toBottom = exiledCards.filter { it != cascadeCard }.shuffled()
        for (cardId in toBottom) {
            val result = ZoneTransitionService.moveToZone(
                state = currentState,
                entityId = cardId,
                destinationZone = Zone.LIBRARY,
                options = ZoneEntryOptions(
                    controllerId = controllerId,
                    libraryPlacement = LibraryPlacement.Bottom
                )
            )
            currentState = result.state
            allEvents.addAll(result.events)
        }

        return EffectResult.success(currentState, allEvents)
    }
}
