package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Warstorm Surge.
 *
 * Card reference:
 * - Warstorm Surge ({5}{R}): Enchantment
 *   "Whenever a creature you control enters, it deals damage equal to its power to any target."
 *
 * Exercises the new `DynamicAmounts.triggeringPower()` facade plus the existing
 * `damageSource = EffectTarget.TriggeringEntity` plumbing — the entering creature
 * is the damage source, not Warstorm Surge itself.
 */
class WarstormSurgeScenarioTest : ScenarioTestBase() {

    init {
        context("Warstorm Surge ETB damage trigger") {

            test("creature you cast deals damage equal to its power to target opponent") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Warstorm Surge")
                    .withCardInHand(1, "Glory Seeker") // 2/2, {1}{W}
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Glory Seeker")
                game.resolveStack()

                // Trigger now on the stack — choose target opponent
                withClue("Should have pending target decision") {
                    game.hasPendingDecision() shouldBe true
                }
                game.selectTargets(listOf(game.player2Id))
                game.resolveStack()

                withClue("Glory Seeker (2/2) should deal 2 damage to opponent") {
                    game.getLifeTotal(2) shouldBe 18
                }
                withClue("Glory Seeker should still be on the battlefield") {
                    game.isOnBattlefield("Glory Seeker") shouldBe true
                }
            }

            test("entering creature kills target creature equal to its power") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Warstorm Surge")
                    .withCardOnBattlefield(2, "Grizzly Bears") // 2/2 target dummy
                    .withCardInHand(1, "Glory Seeker") // 2/2
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Glory Seeker")
                game.resolveStack()

                val bearsId = game.findPermanent("Grizzly Bears")!!
                game.selectTargets(listOf(bearsId))
                game.resolveStack()

                withClue("Grizzly Bears should be destroyed by 2 damage") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
                withClue("Opponent's life should be unchanged (no player damage)") {
                    game.getLifeTotal(2) shouldBe 20
                }
            }
        }
    }
}
