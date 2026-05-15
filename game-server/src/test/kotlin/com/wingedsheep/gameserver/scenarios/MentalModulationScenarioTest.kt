package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Mental Modulation.
 *
 * Card reference:
 * - Mental Modulation ({1}{U}): Instant
 *   This spell costs {1} less to cast during your turn.
 *   Tap target artifact or creature.
 *   Draw a card.
 *
 * Exercises [com.wingedsheep.sdk.scripting.CostReductionSource.FixedIfCondition]
 * with [com.wingedsheep.sdk.scripting.conditions.IsYourTurn].
 */
class MentalModulationScenarioTest : ScenarioTestBase() {

    init {
        context("Mental Modulation own-turn cost reduction") {

            test("costs {U} during caster's own turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mental Modulation")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 1) // Only 1 Island — needs reduction
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Grizzly Bears")!!
                val castResult = game.castSpell(1, "Mental Modulation", targetId = target)
                withClue("Cast should succeed with own-turn reduction: ${castResult.error}") {
                    castResult.error shouldBe null
                }
            }

            test("costs full {1}{U} during opponent's turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mental Modulation")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 1) // 1 mana is not enough off-turn
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Grizzly Bears")!!
                val castResult = game.castSpell(1, "Mental Modulation", targetId = target)
                withClue("Cast should fail without enough mana on opponent's turn") {
                    castResult.error shouldBe "Not enough mana to cast this spell"
                }
            }

            test("can be cast at full cost {1}{U} on opponent's turn with 2 mana") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mental Modulation")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Grizzly Bears")!!
                val castResult = game.castSpell(1, "Mental Modulation", targetId = target)
                withClue("Cast should succeed with full mana on opponent's turn: ${castResult.error}") {
                    castResult.error shouldBe null
                }
            }
        }
    }
}
