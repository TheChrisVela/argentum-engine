package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.ChosenColorComponent

class ColorChoiceContinuationResumer(
    private val services: com.wingedsheep.engine.core.EngineServices,
    private val effectRunner: EffectContinuationRunner
) : ContinuationResumerModule {

    private val tappedForManaBonusResolver =
        com.wingedsheep.engine.handlers.effects.mana.TappedForManaBonusResolver(services.cardRegistry)

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(ChooseColorThenContinuation::class, ::resumeChooseColorThen),
        resumer(ChooseNumberThenContinuation::class, ::resumeChooseNumberThen),
        resumer(ChooseManaColorContinuation::class, ::resumeChooseManaColor),
        resumer(ChooseColorForTargetContinuation::class, ::resumeChooseColorForTarget),
        resumer(ChooseAnyColorTapBonusContinuation::class, ::resumeChooseAnyColorTapBonus)
    )

    fun resumeChooseAnyColorTapBonus(
        state: GameState,
        continuation: ChooseAnyColorTapBonusContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult = tappedForManaBonusResolver.resume(state, continuation, response, checkForMore)

    fun resumeChooseColorThen(
        state: GameState,
        continuation: ChooseColorThenContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is ColorChosenResponse) {
            return ExecutionResult.error(state, "Expected color choice response for ChooseColorThen effect")
        }

        val contextWithColor = continuation.baseContext.copy(chosenColor = response.color)
        val effectResult = effectRunner.executeRemainingEffects(
            state,
            listOf(continuation.then),
            contextWithColor
        )

        if (effectResult.isPaused) return effectResult.toExecutionResult()
        return checkForMore(effectResult.state, effectResult.events.toList())
    }

    fun resumeChooseNumberThen(
        state: GameState,
        continuation: ChooseNumberThenContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is NumberChosenResponse) {
            return ExecutionResult.error(state, "Expected number choice response for ChooseNumberThen effect")
        }

        // Stamp the chosen number as X so atomic effects/filters (manaValueEqualsX) read it.
        val contextWithNumber = continuation.baseContext.copy(xValue = response.number)
        val effectResult = effectRunner.executeRemainingEffects(
            state,
            listOf(continuation.then),
            contextWithNumber
        )

        if (effectResult.isPaused) return effectResult.toExecutionResult()
        return checkForMore(effectResult.state, effectResult.events.toList())
    }

    fun resumeChooseManaColor(
        state: GameState,
        continuation: ChooseManaColorContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is ColorChosenResponse) {
            return ExecutionResult.error(state, "Expected color choice response for AddManaOfChoice effect")
        }

        val contextWithColor = continuation.baseContext.copy(manaColorChoice = response.color)
        val effectResult = effectRunner.executeRemainingEffects(
            state,
            listOf(continuation.effect),
            contextWithColor
        )

        if (effectResult.isPaused) return effectResult.toExecutionResult()
        return checkForMore(effectResult.state, effectResult.events.toList())
    }

    fun resumeChooseColorForTarget(
        state: GameState,
        continuation: ChooseColorForTargetContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is ColorChosenResponse) {
            return ExecutionResult.error(state, "Expected color choice response")
        }

        val targetId = continuation.targetEntityId
        if (!state.getBattlefield().contains(targetId) || state.getEntity(targetId) == null) {
            return checkForMore(state, emptyList())
        }

        val newState = state.updateEntity(targetId) { container ->
            container.with(ChosenColorComponent(response.color))
        }

        return checkForMore(newState, emptyList())
    }
}
