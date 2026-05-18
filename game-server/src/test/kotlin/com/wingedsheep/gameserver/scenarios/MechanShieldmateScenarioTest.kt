package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.player.PermanentTypesEnteredBattlefieldThisTurnComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Mechan Shieldmate (EOE #65).
 *
 * Card reference:
 * - Mechan Shieldmate {1}{U} — Artifact Creature — Robot Soldier 3/2
 *   Defender
 *   As long as an artifact entered the battlefield under your control this turn, this creature
 *   can attack as though it didn't have defender.
 *
 * The "an artifact entered ... this turn" condition is backed by a per-player ETB tracker
 * (`PermanentTypesEnteredBattlefieldThisTurnComponent`) so the artifact need not still be on
 * the battlefield, still be an artifact, or still be under the same controller when combat
 * happens — only the entry event matters (Scryfall ruling, 2025-07-25).
 */
class MechanShieldmateScenarioTest : ScenarioTestBase() {

    init {
        context("Mechan Shieldmate - CanAttackDespiteDefender") {

            test("cannot attack when no artifact entered this turn (defender restriction applies)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val result = game.declareAttackers(mapOf("Mechan Shieldmate" to 2))
                withClue("Mechan Shieldmate should not be able to attack with no artifact entering this turn") {
                    (result.error != null) shouldBe true
                }
            }

            test("can attack when an artifact has entered the battlefield this turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Simulate an artifact having entered the battlefield under P1 earlier this turn
                // by populating the per-player tracker directly. (`withCardOnBattlefield` puts
                // entities into the battlefield zone without going through the ZoneTransitionService
                // path that records the ETB, so we set the tracker by hand.)
                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(
                        PermanentTypesEnteredBattlefieldThisTurnComponent(setOf(CardType.ARTIFACT))
                    )
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val result = game.declareAttackers(mapOf("Mechan Shieldmate" to 2))
                withClue("Mechan Shieldmate should be able to attack after an artifact entered this turn") {
                    result.error shouldBe null
                }
            }

            test("ruling edge case: still able to attack even if the artifact has since left") {
                // The tracker stays true for the rest of the turn after the entry event, even if
                // the artifact later leaves the battlefield, changes type, or changes controllers.
                // We model this by setting the tracker without leaving any artifact on the
                // battlefield — the only signal is the per-turn record.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(
                        PermanentTypesEnteredBattlefieldThisTurnComponent(setOf(CardType.ARTIFACT))
                    )
                }

                // Mechan Shieldmate itself is the only artifact on the battlefield, and it was
                // already on the battlefield at the start of the turn (no ETB recorded for it).
                // The tracker alone should still satisfy the static ability's condition.
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val result = game.declareAttackers(mapOf("Mechan Shieldmate" to 2))
                withClue("Tracker alone (no live artifact on battlefield) should still enable the attack") {
                    result.error shouldBe null
                }
            }

            test("a non-artifact entering does not enable Mechan Shieldmate") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Tracker records only a non-artifact entry (e.g., a creature with no artifact type).
                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(
                        PermanentTypesEnteredBattlefieldThisTurnComponent(setOf(CardType.CREATURE))
                    )
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val result = game.declareAttackers(mapOf("Mechan Shieldmate" to 2))
                withClue("Only artifact ETBs should satisfy the condition") {
                    (result.error != null) shouldBe true
                }
            }
        }
    }
}
