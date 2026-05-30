package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Rogue Kavu's attacks-alone trigger:
 * "Whenever this creature attacks alone, it gets +2/+0 until end of turn."
 *
 * Base stats are 1/1.
 */
class RogueKavuScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Rogue Kavu attacks alone") {

            test("gets +2/+0 when attacking alone") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Rogue Kavu")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attackResult = game.declareAttackers(mapOf("Rogue Kavu" to 2))
                withClue("Declaring attack should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }
                // Resolve the attacks-alone trigger.
                game.resolveStack()

                val kavuId = game.findPermanent("Rogue Kavu")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should be 1 base + 2 from attacking alone") {
                    projected.getPower(kavuId) shouldBe 3
                }
                withClue("Toughness stays at 1 (+2/+0)") {
                    projected.getToughness(kavuId) shouldBe 1
                }
            }

            test("does not get the bonus when attacking alongside another creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Rogue Kavu")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attackResult = game.declareAttackers(
                    mapOf("Rogue Kavu" to 2, "Grizzly Bears" to 2)
                )
                withClue("Declaring attack should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }
                game.resolveStack()

                val kavuId = game.findPermanent("Rogue Kavu")!!
                val projected = stateProjector.project(game.state)

                withClue("Power should stay at base 1 (not attacking alone)") {
                    projected.getPower(kavuId) shouldBe 1
                }
                withClue("Toughness should stay at base 1") {
                    projected.getToughness(kavuId) shouldBe 1
                }
            }
        }
    }
}
