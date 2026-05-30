package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.WasKickedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Pouncing Kavu with kicker.
 *
 * Card reference:
 * - Pouncing Kavu ({1}{R}): Creature — Kavu 1/1
 *   Kicker {2}{R}
 *   First strike
 *   If this creature was kicked, it enters with two +1/+1 counters on it and with haste.
 */
class PouncingKavuScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private fun ScenarioTestBase.TestGame.getCounters(entityId: EntityId): Int {
        return state.getEntity(entityId)
            ?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    init {
        context("Pouncing Kavu kicker") {

            test("unkicked enters as a 1/1 first striker with no counters and no haste") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Pouncing Kavu")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Pouncing Kavu")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val kavuId = game.findPermanent("Pouncing Kavu")!!
                val projected = stateProjector.project(game.state)

                withClue("Unkicked Pouncing Kavu should have no +1/+1 counters") {
                    game.getCounters(kavuId) shouldBe 0
                }
                withClue("Should have first strike") {
                    projected.hasKeyword(kavuId, Keyword.FIRST_STRIKE) shouldBe true
                }
                withClue("Unkicked Pouncing Kavu should NOT have haste") {
                    projected.hasKeyword(kavuId, Keyword.HASTE) shouldBe false
                }
            }

            test("kicked enters with two +1/+1 counters and haste") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Pouncing Kavu")
                    .withLandsOnBattlefield(1, "Mountain", 5) // {1}{R} + {2}{R} kicker
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val playerId = game.player1Id
                val hand = game.state.getHand(playerId)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Pouncing Kavu"
                }!!

                val castResult = game.execute(CastSpell(playerId, cardId, wasKicked = true))
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val kavuId = game.findPermanent("Pouncing Kavu")!!

                withClue("Permanent should have WasKickedComponent") {
                    (game.state.getEntity(kavuId)?.has<WasKickedComponent>() == true) shouldBe true
                }
                withClue("Kicked Pouncing Kavu should have two +1/+1 counters") {
                    game.getCounters(kavuId) shouldBe 2
                }

                val projected = stateProjector.project(game.state)
                withClue("Should have first strike") {
                    projected.hasKeyword(kavuId, Keyword.FIRST_STRIKE) shouldBe true
                }
                withClue("Kicked Pouncing Kavu should have haste") {
                    projected.hasKeyword(kavuId, Keyword.HASTE) shouldBe true
                }
            }
        }
    }
}
