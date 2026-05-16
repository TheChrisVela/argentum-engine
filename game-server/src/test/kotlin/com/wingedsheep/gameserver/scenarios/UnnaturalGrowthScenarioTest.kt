package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Unnatural Growth.
 *
 * Card reference:
 * - Unnatural Growth ({1}{G}{G}{G}{G}): Enchantment
 *   "At the beginning of each combat, double the power and toughness of each creature
 *    you control until end of turn."
 *
 * Exercises the new `Triggers.EachCombat` trigger (beginning of combat on any player's turn)
 * and group P/T doubling via `ForEachInGroupEffect` + `EntityReference.IterationEntity`.
 */
class UnnaturalGrowthScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Unnatural Growth - doubles creatures' P/T each combat") {
            test("doubles your creatures' P/T at beginning of combat on your turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Unnatural Growth")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withCardOnBattlefield(1, "Festering Goblin") // 1/1
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gloryId = game.findPermanent("Glory Seeker")!!
                val goblinId = game.findPermanent("Festering Goblin")!!

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Glory Seeker (2/2) should be 4/4 after doubling") {
                    projected.getPower(gloryId) shouldBe 4
                    projected.getToughness(gloryId) shouldBe 4
                }
                withClue("Festering Goblin (1/1) should be 2/2 after doubling") {
                    projected.getPower(goblinId) shouldBe 2
                    projected.getToughness(goblinId) shouldBe 2
                }
            }

            test("triggers on opponent's combat too (each combat)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Unnatural Growth")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withActivePlayer(2) // opponent's turn
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gloryId = game.findPermanent("Glory Seeker")!!

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Even on opponent's turn, your Glory Seeker should be doubled to 4/4") {
                    projected.getPower(gloryId) shouldBe 4
                    projected.getToughness(gloryId) shouldBe 4
                }
            }

            test("does not affect opponent's creatures") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Unnatural Growth")
                    .withCardOnBattlefield(2, "Glory Seeker") // opponent's 2/2
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val opponentGloryId = game.findPermanent("Glory Seeker")!!

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Opponent's Glory Seeker should remain 2/2") {
                    projected.getPower(opponentGloryId) shouldBe 2
                    projected.getToughness(opponentGloryId) shouldBe 2
                }
            }
        }
    }
}
