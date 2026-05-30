package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Simoon.
 *
 * Simoon ({R}{G}, Instant): "Simoon deals 1 damage to each creature target opponent
 * controls." Canonical printing is Visions; reprinted in Invasion.
 *
 * Exercises the ControllerPredicate.ControlledByTargetOpponent filter path: only the
 * targeted opponent's creatures are damaged, not the caster's.
 */
class SimoonScenarioTest : ScenarioTestBase() {

    init {
        context("Simoon") {

            test("deals 1 damage to each creature the targeted opponent controls only") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Simoon")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Forest", 1) // {R}{G}
                    .withCardOnBattlefield(1, "Hill Giant") // caster's 3/3 — must be untouched
                    .withCardOnBattlefield(2, "Jungle Lion") // opponent's 2/1 — dies
                    .withCardOnBattlefield(2, "Coral Eel") // opponent's 2/1 — dies
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpellTargetingPlayer(1, "Simoon", 2)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Each of the targeted opponent's 2/1 creatures dies to 1 damage") {
                    game.isOnBattlefield("Jungle Lion") shouldBe false
                    game.isOnBattlefield("Coral Eel") shouldBe false
                    game.isInGraveyard(2, "Jungle Lion") shouldBe true
                    game.isInGraveyard(2, "Coral Eel") shouldBe true
                }
                withClue("The caster's own creature takes no damage") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }
            }
        }
    }
}
