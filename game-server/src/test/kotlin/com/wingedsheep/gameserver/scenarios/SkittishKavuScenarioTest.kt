package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Skittish Kavu's conditional static ability:
 * "This creature gets +1/+1 as long as no opponent controls a white or blue creature."
 *
 * Base stats are 1/1.
 */
class SkittishKavuScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Skittish Kavu conditional buff") {

            test("is 2/2 when no opponent controls a white or blue creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Skittish Kavu")
                    // Opponent controls a non-white, non-blue creature (Grizzly Bears is green)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val kavuId = game.findPermanent("Skittish Kavu")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should be buffed to 2") {
                    projected.getPower(kavuId) shouldBe 2
                }
                withClue("Toughness should be buffed to 2") {
                    projected.getToughness(kavuId) shouldBe 2
                }
            }

            test("is 1/1 when an opponent controls a white creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Skittish Kavu")
                    // Glory Seeker is a white creature
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val kavuId = game.findPermanent("Skittish Kavu")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should stay at base 1") {
                    projected.getPower(kavuId) shouldBe 1
                }
                withClue("Toughness should stay at base 1") {
                    projected.getToughness(kavuId) shouldBe 1
                }
            }
        }
    }
}
