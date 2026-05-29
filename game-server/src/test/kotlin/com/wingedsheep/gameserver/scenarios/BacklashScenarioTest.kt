package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Backlash.
 *
 * Backlash ({1}{B}{R}, Instant): Tap target untapped creature. That creature deals damage
 * equal to its power to its controller.
 */
class BacklashScenarioTest : ScenarioTestBase() {

    init {
        context("Backlash") {

            test("taps the target and deals power damage to its controller") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Backlash")
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, untapped
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!

                val castResult = game.castSpell(1, "Backlash", giant)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant should be tapped") {
                    (game.state.getEntity(giant)?.has<TappedComponent>() == true) shouldBe true
                }
                withClue("Hill Giant should still be on the battlefield") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }
                withClue("Opponent should take 3 damage (Hill Giant's power): 20 -> 17") {
                    game.getLifeTotal(2) shouldBe 17
                }
                withClue("Caster's life is unaffected") {
                    game.getLifeTotal(1) shouldBe 20
                }
            }
        }
    }
}
