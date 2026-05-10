package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Insatiable Skittermaw.
 *
 * Card reference:
 * - Insatiable Skittermaw ({2}{B}): 2/2 Creature — Insect Horror
 *   "Menace.
 *    Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn
 *    or a spell was warped this turn, put a +1/+1 counter on this creature."
 */
class InsatiableSkittermawScenarioTest : ScenarioTestBase() {

    init {
        context("Insatiable Skittermaw void trigger") {

            test("Void NOT met: no +1/+1 counter at end step") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Insatiable Skittermaw")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val skittermawId = game.findPermanent("Insatiable Skittermaw")!!

                game.passUntilPhase(Phase.ENDING, Step.END)

                withClue("No +1/+1 counter when Void not met") {
                    val counters = game.state.getEntity(skittermawId)!!.get<CountersComponent>()
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
                withClue("Skittermaw remains 2/2") {
                    game.state.projectedState.getPower(skittermawId) shouldBe 2
                    game.state.projectedState.getToughness(skittermawId) shouldBe 2
                }
            }

            test("Void met: +1/+1 counter at end step grows Skittermaw to 3/3") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Insatiable Skittermaw")
                    .withCardOnBattlefield(2, "Devoted Hero") // 1/2 — Shock fodder for Void
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInHand(1, "Shock")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val skittermawId = game.findPermanent("Insatiable Skittermaw")!!
                val heroId = game.findPermanent("Devoted Hero")!!

                // Shock the opposing Hero — its death satisfies Void.
                game.castSpell(1, "Shock", heroId)
                game.resolveStack()
                withClue("Devoted Hero should be dead") { game.isInGraveyard(2, "Devoted Hero") shouldBe true }

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                withClue("Skittermaw should have 1 +1/+1 counter") {
                    val counters = game.state.getEntity(skittermawId)!!.get<CountersComponent>()
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
                }
                withClue("Skittermaw projected power 2 + 1 = 3") {
                    game.state.projectedState.getPower(skittermawId) shouldBe 3
                }
                withClue("Skittermaw projected toughness 2 + 1 = 3") {
                    game.state.projectedState.getToughness(skittermawId) shouldBe 3
                }
            }
        }
    }
}
