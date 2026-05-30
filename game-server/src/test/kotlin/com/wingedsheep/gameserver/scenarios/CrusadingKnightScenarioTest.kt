package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Crusading Knight:
 * "Protection from black. This creature gets +1/+1 for each Swamp your opponents control."
 *
 * Base stats are 2/2.
 */
class CrusadingKnightScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Crusading Knight dynamic stats") {

            test("with no opponent Swamps, stays 2/2") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Crusading Knight")
                    .withLandsOnBattlefield(1, "Swamp", 3) // own Swamps don't count
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val knightId = game.findPermanent("Crusading Knight")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should stay at base 2 — your own Swamps don't count") {
                    projected.getPower(knightId) shouldBe 2
                }
                withClue("Toughness should stay at base 2") {
                    projected.getToughness(knightId) shouldBe 2
                }
            }

            test("with three opponent Swamps, gets +3/+3") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Crusading Knight")
                    .withLandsOnBattlefield(2, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val knightId = game.findPermanent("Crusading Knight")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should be 2 base + 3 (three opponent Swamps)") {
                    projected.getPower(knightId) shouldBe 5
                }
                withClue("Toughness should be 2 base + 3") {
                    projected.getToughness(knightId) shouldBe 5
                }
            }
        }
    }
}
