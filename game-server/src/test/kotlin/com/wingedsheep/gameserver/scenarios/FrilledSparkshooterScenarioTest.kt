package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.LifeLostThisTurnComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Frilled Sparkshooter — issue #48.
 *
 * Card reference:
 * - Frilled Sparkshooter ({3}{R}): Creature — Lizard Archer 3/3
 *   Menace, reach
 *   This creature enters with a +1/+1 counter on it if an opponent lost life this turn.
 *
 * Counter placement is a replacement effect (Rule 614), not a triggered ability.
 */
class FrilledSparkshooterScenarioTest : ScenarioTestBase() {

    init {
        context("Frilled Sparkshooter enters-with-counter replacement") {

            test("enters with a +1/+1 counter when an opponent lost life this turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Frilled Sparkshooter")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player2Id) { container ->
                    container.with(LifeLostThisTurnComponent)
                }

                val castResult = game.castSpell(1, "Frilled Sparkshooter")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                val sparkshooterId = game.findPermanent("Frilled Sparkshooter")!!
                val counters = game.state.getEntity(sparkshooterId)?.get<CountersComponent>()
                withClue("Frilled Sparkshooter should enter with a +1/+1 counter") {
                    counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }
            }

            test("enters with no counter when no opponent has lost life this turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Frilled Sparkshooter")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Frilled Sparkshooter")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                val sparkshooterId = game.findPermanent("Frilled Sparkshooter")!!
                val counters = game.state.getEntity(sparkshooterId)?.get<CountersComponent>()
                withClue("Frilled Sparkshooter should enter with no +1/+1 counter") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
            }
        }
    }
}
