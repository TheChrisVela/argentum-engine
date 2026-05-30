package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Shoreline Raider: "Protection from Kavu" (Rule 702.16).
 *
 * Protection from Kavu means a Kavu creature cannot block Shoreline Raider (the "B" in DEBT).
 */
class ShorelineRaiderScenarioTest : ScenarioTestBase() {

    init {
        context("Shoreline Raider protection from Kavu") {

            test("Kavu creature cannot block Shoreline Raider") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Shoreline Raider") // 2/2, pro Kavu
                    .withCardOnBattlefield(2, "Kavu Climber")     // 3/3 Kavu
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Shoreline Raider" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val blockResult = game.declareBlockers(mapOf(
                    "Kavu Climber" to listOf("Shoreline Raider")
                ))
                withClue("Kavu creature should not be able to block a creature with protection from Kavu") {
                    blockResult.error shouldNotBe null
                }
            }

            test("non-Kavu creature CAN block Shoreline Raider") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Shoreline Raider") // 2/2, pro Kavu
                    .withCardOnBattlefield(2, "Grizzly Bears")    // 2/2, non-Kavu
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Shoreline Raider" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val blockResult = game.declareBlockers(mapOf(
                    "Grizzly Bears" to listOf("Shoreline Raider")
                ))
                withClue("Non-Kavu creature should be able to block: ${blockResult.error}") {
                    blockResult.error shouldBe null
                }
            }
        }
    }
}
