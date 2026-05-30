package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Slinking Serpent: "Forestwalk" (Rule 702.14).
 *
 * Slinking Serpent (2/3) can't be blocked as long as the defending player controls a Forest.
 */
class SlinkingSerpentScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Slinking Serpent forestwalk") {

            test("has forestwalk keyword") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Slinking Serpent")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val serpentId = game.findPermanent("Slinking Serpent")!!
                val projected = stateProjector.project(game.state)

                withClue("Slinking Serpent should have forestwalk") {
                    projected.hasKeyword(serpentId, Keyword.FORESTWALK) shouldBe true
                }
            }

            test("cannot be blocked when defender controls a Forest") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Slinking Serpent")
                    .withCardOnBattlefield(2, "Grizzly Bears") // 2/2 potential blocker
                    .withLandsOnBattlefield(2, "Forest", 1)    // defender controls a Forest
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Slinking Serpent" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val blockResult = game.declareBlockers(mapOf(
                    "Grizzly Bears" to listOf("Slinking Serpent")
                ))
                withClue("Slinking Serpent should be unblockable while defender controls a Forest") {
                    blockResult.error shouldNotBe null
                }
            }

            test("can be blocked when defender controls no Forest") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Slinking Serpent")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(2, "Island", 1) // no Forest
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Slinking Serpent" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val blockResult = game.declareBlockers(mapOf(
                    "Grizzly Bears" to listOf("Slinking Serpent")
                ))
                withClue("Slinking Serpent should be blockable when defender has no Forest: ${blockResult.error}") {
                    blockResult.error shouldBe null
                }
            }
        }
    }
}
