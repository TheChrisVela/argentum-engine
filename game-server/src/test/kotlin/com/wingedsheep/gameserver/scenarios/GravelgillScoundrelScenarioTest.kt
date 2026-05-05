package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class GravelgillScoundrelScenarioTest : ScenarioTestBase() {

    init {
        context("Gravelgill Scoundrel attack trigger") {

            test("does not present the may-tap decision when the controller has no other untapped creatures") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Gravelgill Scoundrel")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Gravelgill Scoundrel" to 2))

                // Resolve until either a decision pops or the stack drains.
                while (game.state.stack.isNotEmpty() && !game.hasPendingDecision()) {
                    game.passPriority()
                }

                withClue(
                    "Without another untapped creature to tap, the may-tap action is impossible — " +
                        "the engine must skip the yes/no entirely so saying 'yes' can't grant unblockable for free"
                ) {
                    (game.getPendingDecision() is YesNoDecision) shouldBe false
                }
            }

            test("still presents the may-tap decision when another untapped creature is available") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Gravelgill Scoundrel")
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Gravelgill Scoundrel" to 2))

                while (game.state.stack.isNotEmpty() && !game.hasPendingDecision()) {
                    game.passPriority()
                }

                withClue("The controller should still get to choose whether to tap their other creature") {
                    val decision = game.getPendingDecision()
                    (decision is YesNoDecision) shouldBe true
                }
            }
        }
    }
}
