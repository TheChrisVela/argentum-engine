package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffects
import com.wingedsheep.engine.mechanics.layers.createFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.SuspectEffect
import kotlin.reflect.KClass

/**
 * Applies the Suspect mechanic to a target permanent.
 *
 * Adds three floating effects with a shared timestamp so they are treated
 * as a single atomic application under Rule 613:
 *  - SetSuspected  (queryable first-class status)
 *  - GrantKeyword(MENACE)
 *  - SetCantBlock
 */
class SuspectEffectHandler : EffectExecutor<SuspectEffect> {

    override val effectType: KClass<SuspectEffect> = SuspectEffect::class

    override fun execute(
        state: GameState,
        effect: SuspectEffect,
        context: EffectContext
    ): EffectResult {
        val entityId = TargetResolutionUtils.resolveTarget(effect.target, context)
            ?: return EffectResult.success(state)
        state.getEntity(entityId)?.get<CardComponent>()
            ?: return EffectResult.success(state)

        val sharedTimestamp = state.timestamp
        val affected = setOf(entityId)

        val newState = state.addFloatingEffects(
            listOf(
                state.createFloatingEffect(
                    layer = Layer.ABILITY,
                    modification = SerializableModification.SetSuspected,
                    affectedEntities = affected,
                    duration = effect.duration,
                    context = context,
                    timestamp = sharedTimestamp
                ),
                state.createFloatingEffect(
                    layer = Layer.ABILITY,
                    modification = SerializableModification.GrantKeyword(Keyword.MENACE.name),
                    affectedEntities = affected,
                    duration = effect.duration,
                    context = context,
                    timestamp = sharedTimestamp
                ),
                state.createFloatingEffect(
                    layer = Layer.ABILITY,
                    modification = SerializableModification.SetCantBlock,
                    affectedEntities = affected,
                    duration = effect.duration,
                    context = context,
                    timestamp = sharedTimestamp
                )
            )
        )

        return EffectResult.success(newState)
    }
}
