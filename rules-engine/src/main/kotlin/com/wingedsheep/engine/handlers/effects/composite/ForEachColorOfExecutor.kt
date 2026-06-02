package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.ForEachColorOfEffect
import com.wingedsheep.sdk.scripting.values.EntityReference
import kotlin.reflect.KClass

/**
 * Executor for [ForEachColorOfEffect] — the non-interactive sibling of `ChooseColorThen`.
 *
 * Resolves the source entity from its [EntityReference], reads its colors, then runs the inner
 * effect once per color with that color placed in [EffectContext.chosenColor] (the same channel
 * `ChooseColorThen` feeds), so per-color atoms like `GrantProtectionFromChosenColor` compose
 * inside it. Iterates in canonical WUBRG order for deterministic events.
 *
 * Source colors are read from projected state while the source is on the battlefield (so Layer-5
 * "becomes colorless"/Devoid and color-adding effects are honored, including an authoritative
 * empty set), otherwise from its base printed colors as last-known information (CR 113.7a). A
 * colorless source runs the inner effect zero times (colorless is not a color, CR 105.2).
 */
class ForEachColorOfExecutor(
    private val effectExecutor: (GameState, Effect, EffectContext) -> EffectResult
) : EffectExecutor<ForEachColorOfEffect> {

    override val effectType: KClass<ForEachColorOfEffect> = ForEachColorOfEffect::class

    override fun execute(
        state: GameState,
        effect: ForEachColorOfEffect,
        context: EffectContext
    ): EffectResult {
        val sourceId = resolveEntityId(effect.source, context, state)
            ?: return EffectResult.success(state)

        val colorNames = readSourceColors(state, sourceId)
        // Canonical WUBRG order for deterministic event sequencing; skips colors not present.
        val colors = Color.entries.filter { it.name in colorNames }
        if (colors.isEmpty()) return EffectResult.success(state)

        var currentState = state
        val events = mutableListOf<GameEvent>()
        for (color in colors) {
            val innerContext = context.copy(chosenColor = color)
            val result = effectExecutor(currentState, effect.effect, innerContext)
            if (result.isPaused) {
                return EffectResult.paused(result.state, result.pendingDecision!!, events + result.events)
            }
            currentState = result.newState
            events.addAll(result.events)
        }
        return EffectResult.success(currentState, events)
    }

    private fun resolveEntityId(ref: EntityReference, context: EffectContext, state: GameState): EntityId? =
        when (ref) {
            is EntityReference.Source -> context.sourceId
            is EntityReference.EnchantedCreature ->
                context.sourceId?.let { state.getEntity(it)?.get<AttachedToComponent>()?.targetId }
            is EntityReference.Target -> {
                when (val target = context.targets.getOrNull(ref.index)) {
                    is com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent -> target.entityId
                    is com.wingedsheep.engine.state.components.stack.ChosenTarget.Card -> target.cardId
                    is com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell -> target.spellEntityId
                    else -> null
                }
            }
            is EntityReference.Sacrificed -> context.sacrificedPermanents.getOrNull(ref.index)?.entityId
            is EntityReference.TappedAsCost -> context.tappedPermanents.getOrNull(ref.index)
            is EntityReference.Triggering -> context.triggeringEntityId
            is EntityReference.AffectedEntity -> context.affectedEntityId
            is EntityReference.IterationEntity -> context.pipeline.iterationTarget
            is EntityReference.FromCostStorage ->
                context.pipeline.storedCollections[ref.collectionName]?.getOrNull(ref.index)
            is EntityReference.AmassedArmy ->
                context.pipeline.storedCollections[EntityReference.AmassedArmy.STORAGE_KEY]?.firstOrNull()
        }

    private fun readSourceColors(state: GameState, entityId: EntityId): Set<String> {
        // On battlefield → projection is authoritative, including an empty set (the source is
        // legitimately colorless via Devoid or a Layer-5 "becomes colorless" effect) and any
        // colors added by a Layer-5 effect. Off battlefield → no projectedValues entry exists,
        // so fall back to base printed colors as last-known-information.
        if (state.getBattlefield().contains(entityId)) {
            return state.projectedState.getColors(entityId)
        }
        val card = state.getEntity(entityId)?.get<CardComponent>() ?: return emptySet()
        return card.colors.map { it.name }.toSet()
    }
}
