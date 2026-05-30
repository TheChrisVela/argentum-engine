package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Kavu Scout's Domain static ability:
 * "Domain — This creature gets +1/+0 for each basic land type among lands you control."
 *
 * Base stats are 0/2.
 */
class KavuScoutScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Kavu Scout Domain") {

            test("with one basic land type, gets +1/+0") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Kavu Scout")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val kavuId = game.findPermanent("Kavu Scout")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should be 0 base + 1 (one basic land type)") {
                    projected.getPower(kavuId) shouldBe 1
                }
                withClue("Toughness stays at 2 (Domain only adds power)") {
                    projected.getToughness(kavuId) shouldBe 2
                }
            }

            test("with three basic land types, gets +3/+0") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Kavu Scout")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val kavuId = game.findPermanent("Kavu Scout")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should be 0 base + 3 (three basic land types)") {
                    projected.getPower(kavuId) shouldBe 3
                }
                withClue("Toughness stays at 2") {
                    projected.getToughness(kavuId) shouldBe 2
                }
            }
        }
    }
}
