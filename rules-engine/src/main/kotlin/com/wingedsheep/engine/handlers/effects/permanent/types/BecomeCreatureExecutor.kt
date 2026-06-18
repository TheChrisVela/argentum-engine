package com.wingedsheep.engine.handlers.effects.permanent.types

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.Sublayer
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.BecomeCreatureEffect
import kotlin.reflect.KClass

/**
 * Executor for BecomeCreatureEffect.
 * Turns a permanent into a creature by creating floating effects across multiple layers.
 *
 * Used for Sarkhan, the Dragonspeaker's +1 and similar "becomes a creature" effects.
 * Creates all floating effects atomically to avoid validation issues with intermediate states.
 */
class BecomeCreatureExecutor : EffectExecutor<BecomeCreatureEffect> {

    private val amountEvaluator: DynamicAmountEvaluator = DynamicAmountEvaluator()

    override val effectType: KClass<BecomeCreatureEffect> = BecomeCreatureEffect::class

    override fun execute(
        state: GameState,
        effect: BecomeCreatureEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state)

        // Verify the target is still on the battlefield
        if (targetId !in state.getBattlefield()) {
            return EffectResult.success(state)
        }

        val affectedEntities = setOf(targetId)

        // Layer 4 (TYPE): Add CREATURE type
        var newState = state.addFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.AddType("CREATURE"),
            affectedEntities = affectedEntities,
            duration = effect.duration,
            context = context
        )

        // Layer 4 (TYPE): Remove specified types (e.g., PLANESWALKER)
        for (type in effect.removeTypes) {
            newState = newState.addFloatingEffect(
                layer = Layer.TYPE,
                modification = SerializableModification.RemoveType(type),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                context = context
            )
        }

        // Layer 4 (TYPE): Set creature subtypes
        if (effect.creatureTypes.isNotEmpty()) {
            newState = newState.addFloatingEffect(
                layer = Layer.TYPE,
                modification = SerializableModification.SetCreatureSubtypes(effect.creatureTypes),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                context = context
            )
        }

        // Layer 5 (COLOR): Change color if specified
        if (effect.colors != null) {
            newState = newState.addFloatingEffect(
                layer = Layer.COLOR,
                modification = SerializableModification.ChangeColor(effect.colors!!),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                context = context
            )
        }

        // Layer 6 (ABILITY): Grant keywords
        for (keyword in effect.keywords) {
            newState = newState.addFloatingEffect(
                layer = Layer.ABILITY,
                modification = SerializableModification.GrantKeyword(keyword.name),
                affectedEntities = affectedEntities,
                duration = effect.duration,
                context = context
            )
        }

        // Layer 7b (POWER_TOUGHNESS, SET_VALUES): Set base P/T. The dynamic amounts are evaluated
        // once, now, and stamped as a fixed set-value floating effect (CR 613.4c — the value is
        // locked in when the effect begins to apply; it does not keep recomputing).
        val powerValue = amountEvaluator.evaluate(newState, effect.power, context)
        val toughnessValue = amountEvaluator.evaluate(newState, effect.toughness, context)
        newState = newState.addFloatingEffect(
            layer = Layer.POWER_TOUGHNESS,
            sublayer = Sublayer.SET_VALUES,
            modification = SerializableModification.SetPowerToughness(powerValue, toughnessValue),
            affectedEntities = affectedEntities,
            duration = effect.duration,
            context = context
        )

        return EffectResult.success(newState)
    }
}
