package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.ForEachInCollectionEffect
import kotlin.reflect.KClass

/**
 * Executor for ForEachInCollectionEffect.
 *
 * Iterates the entities in a named pipeline collection (snapshotted at resolution) and
 * runs the sub-effect once per entity with `pipeline.iterationTarget` set to it, so a
 * single-target sub-effect referencing `EffectTarget.Self` applies to the current entity.
 *
 * Collection-based sibling of [ForEachInGroupExecutor]; mirrors its per-iteration
 * iterationTarget swap and pause propagation.
 */
class ForEachInCollectionExecutor(
    private val effectExecutor: (GameState, Effect, EffectContext) -> EffectResult
) : EffectExecutor<ForEachInCollectionEffect> {

    override val effectType: KClass<ForEachInCollectionEffect> = ForEachInCollectionEffect::class

    override fun execute(
        state: GameState,
        effect: ForEachInCollectionEffect,
        context: EffectContext
    ): EffectResult {
        val entities = context.pipeline.storedCollections[effect.collection].orEmpty()
        if (entities.isEmpty()) {
            return EffectResult.success(state)
        }

        var currentState = state
        val allEvents = mutableListOf<GameEvent>()

        for (entityId in entities) {
            val innerContext = context.copy(
                pipeline = context.pipeline.copy(iterationTarget = entityId)
            )
            val result = effectExecutor(currentState, effect.effect, innerContext)

            if (result.isPaused) {
                return EffectResult.paused(
                    result.state,
                    result.pendingDecision!!,
                    allEvents + result.events
                )
            }

            currentState = result.newState
            allEvents.addAll(result.events)
        }

        return EffectResult.success(currentState, allEvents)
    }
}
