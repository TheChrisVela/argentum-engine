package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Tangle.
 *
 * Tangle ({1}{G}, Instant): "Prevent all combat damage that would be dealt this turn.
 * Each attacking creature doesn't untap during its controller's next untap step."
 *
 * Exercises composing PreventAllCombatDamage with a ForEachInGroup over attacking
 * creatures granting the DOESNT_UNTAP flag for Duration.UntilAfterAffectedControllersNextUntap.
 */
class TangleScenarioTest : ScenarioTestBase() {

    init {
        context("Tangle") {

            test("prevents combat damage and keeps the attacker tapped through its next untap step") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Hill Giant") // 3/3 attacker
                    .withCardInHand(2, "Tangle")
                    .withLandsOnBattlefield(2, "Forest", 2) // {1}{G}
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!
                val startingLife = game.getLifeTotal(2)

                val attackResult = game.execute(
                    DeclareAttackers(game.player1Id, mapOf(giantId to game.player2Id))
                )
                withClue("Attack should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }

                withClue("Attacker is tapped after attacking") {
                    game.state.getEntity(giantId)?.has<TappedComponent>() shouldBe true
                }

                // Active player passes priority so the defender can act.
                game.passPriority()

                // Defender casts Tangle (instant) while attackers are declared.
                val castResult = game.castSpell(2, "Tangle")
                withClue("Tangle should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                // Resolve combat: no damage should be dealt.
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                withClue("Defender takes no combat damage (all prevented)") {
                    game.getLifeTotal(2) shouldBe startingLife
                }

                // Advance to the attacker's controller's (player 1's) next untap step.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.passPriority()
                game.passPriority()
                // Player 2's turn now; play it out.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.passPriority()
                game.passPriority()

                // Player 1's untap step has now occurred (via the upkeep advance).
                withClue("It should be Player 1's turn again") {
                    game.state.activePlayerId shouldBe game.player1Id
                }
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)

                withClue("Hill Giant did not untap during its controller's next untap step") {
                    game.state.getEntity(giantId)?.has<TappedComponent>() shouldBe true
                }
            }
        }
    }
}
