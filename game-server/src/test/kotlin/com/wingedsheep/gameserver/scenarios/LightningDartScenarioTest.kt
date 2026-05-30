package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Lightning Dart.
 *
 * Lightning Dart ({1}{R}, Instant): "Lightning Dart deals 1 damage to target creature.
 * If that creature is white or blue, Lightning Dart deals 4 damage to it instead."
 *
 * Exercises the resolution-time color check via Conditions.TargetMatchesFilter.
 */
class LightningDartScenarioTest : ScenarioTestBase() {

    init {
        context("Lightning Dart") {

            test("deals only 1 damage to a non-white, non-blue creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Lightning Dart")
                    .withLandsOnBattlefield(1, "Mountain", 2) // {1}{R}
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, red
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!

                val castResult = game.castSpell(1, "Lightning Dart", giant)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Red 3/3 takes only 1 damage and survives") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }
            }

            test("deals 4 damage to a white creature, killing it") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Lightning Dart")
                    .withLandsOnBattlefield(1, "Mountain", 2) // {1}{R}
                    .withCardOnBattlefield(2, "Capashen Unicorn") // 1/2, white
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val unicorn = game.findPermanent("Capashen Unicorn")!!

                val castResult = game.castSpell(1, "Lightning Dart", unicorn)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("White 1/2 takes 4 damage and dies") {
                    game.isOnBattlefield("Capashen Unicorn") shouldBe false
                    game.isInGraveyard(2, "Capashen Unicorn") shouldBe true
                }
            }
        }
    }
}
