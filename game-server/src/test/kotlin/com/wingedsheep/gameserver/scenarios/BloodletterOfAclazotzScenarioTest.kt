package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Bloodletter of Aclazotz.
 *
 * Card reference:
 * - Bloodletter of Aclazotz ({1}{B}{B}{B}): Creature — Vampire Demon (2/4) with Flying.
 *   "If an opponent would lose life during your turn, they lose twice that much life
 *    instead. (Damage causes loss of life.)"
 *
 * Implementation lives in `ModifyLifeLoss` (mtg-sdk) and
 * `DamageUtils.applyStaticLifeLossModification` (rules-engine), called from both
 * `LoseLifeExecutor` and `dealDamageToTarget` (CR 119.3).
 */
class BloodletterOfAclazotzScenarioTest : ScenarioTestBase() {

    init {
        context("Bloodletter of Aclazotz - life loss doubling") {

            test("doubles direct life loss to opponent during your turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bloodletter of Aclazotz")
                    .withCardOnBattlefield(1, "Cabal Archon") // 2/2 Cleric, also the activator
                    .withCardOnBattlefield(1, "Cabal Archon") // sacrifice fodder
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingP2 = game.getLifeTotal(2)
                val startingP1 = game.getLifeTotal(1)

                activateCabalArchon(game, controllerNumber = 1, targetPlayerNumber = 2)

                withClue("Opponent should lose 2*2 = 4 life from Cabal Archon's ability") {
                    game.getLifeTotal(2) shouldBe startingP2 - 4
                }
                withClue("Bloodletter only modifies opponent life loss, controller's gain is unchanged") {
                    game.getLifeTotal(1) shouldBe startingP1 + 2
                }
            }

            test("doubles combat damage life loss dealt to opponent during your turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bloodletter of Aclazotz") // 2/4 flier
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingP2 = game.getLifeTotal(2)

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Bloodletter of Aclazotz" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Damage causes loss of life — 2 damage doubles to 4 life lost") {
                    game.getLifeTotal(2) shouldBe startingP2 - 4
                }
            }

            test("does NOT double life loss during the opponent's turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bloodletter of Aclazotz")
                    .withCardOnBattlefield(2, "Cabal Archon")
                    .withCardOnBattlefield(2, "Cabal Archon")
                    .withLandsOnBattlefield(2, "Swamp", 2)
                    .withActivePlayer(2) // opponent's turn
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingP1 = game.getLifeTotal(1)

                activateCabalArchon(game, controllerNumber = 2, targetPlayerNumber = 1)

                withClue("Bloodletter only triggers during its controller's turn") {
                    game.getLifeTotal(1) shouldBe startingP1 - 2
                }
            }

            test("does NOT double life loss to its own controller") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bloodletter of Aclazotz")
                    .withCardOnBattlefield(1, "Cabal Archon")
                    .withCardOnBattlefield(1, "Cabal Archon")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingP1 = game.getLifeTotal(1)

                // Cabal Archon: target player loses 2 life and you gain 2 life.
                // Targeting yourself: lose 2 (NOT doubled — you aren't an opponent), gain 2.
                activateCabalArchon(game, controllerNumber = 1, targetPlayerNumber = 1)

                withClue("Bloodletter's clause says 'an opponent', so the controller's own life loss is unchanged") {
                    game.getLifeTotal(1) shouldBe startingP1 - 2 + 2
                }
            }
        }
    }

    /**
     * Activates Cabal Archon ({B}, sacrifice a Cleric: target player loses 2 life and you gain 2).
     * Sacrifices the *second* Cabal Archon on the battlefield, then resolves the stack.
     */
    private fun activateCabalArchon(
        game: TestGame,
        controllerNumber: Int,
        targetPlayerNumber: Int,
    ) {
        val controllerId = if (controllerNumber == 1) game.player1Id else game.player2Id
        val targetId = if (targetPlayerNumber == 1) game.player1Id else game.player2Id

        val archons = game.state.getBattlefield()
            .filter { game.state.getEntity(it)
                ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Cabal Archon" }
            .filter { game.state.getEntity(it)
                ?.get<com.wingedsheep.engine.state.components.identity.ControllerComponent>()?.playerId == controllerId }
        check(archons.size >= 2) { "Test setup needs at least two Cabal Archons under player $controllerNumber" }

        val activator = archons[0]
        val sacrificed = archons[1]
        val ability = cardRegistry.getCard("Cabal Archon")!!.script.activatedAbilities.first()

        val result = game.execute(
            ActivateAbility(
                playerId = controllerId,
                sourceId = activator,
                abilityId = ability.id,
                targets = listOf(ChosenTarget.Player(targetId)),
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(sacrificed)),
            )
        )
        check(result.error == null) { "Activating Cabal Archon failed: ${result.error}" }
        game.resolveStack()
    }
}
