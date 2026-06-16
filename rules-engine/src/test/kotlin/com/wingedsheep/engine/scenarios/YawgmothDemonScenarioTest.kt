package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Yawgmoth Demon (ATQ #21).
 *
 * {4}{B}{B} Creature — Phyrexian Demon, 6/6, flying, first strike.
 * "At the beginning of your upkeep, you may sacrifice an artifact. If you don't, tap this
 *  creature and it deals 2 damage to you."
 *
 * Modeled with [com.wingedsheep.sdk.scripting.effects.PayOrSufferEffect] ("do [suffer] unless you
 * [cost]"). The upkeep trigger only fires on a real step transition into UPKEEP, so each scenario
 * starts on the opponent's turn (precombat main) and passes around to player 1's upkeep. The
 * punisher flow: with no artifact the sacrifice cost is unpayable and the tax applies with no
 * decision; with an artifact the controller is offered a card selection — picking one pays the
 * cost (no tax), picking none declines (tax applies).
 */
class YawgmothDemonScenarioTest : ScenarioTestBase() {

    private fun isTapped(game: TestGame, id: com.wingedsheep.sdk.model.EntityId): Boolean =
        game.state.getEntity(id)?.get<TappedComponent>() != null

    private fun atPlayer1Upkeep(withArtifact: Boolean): TestGame {
        var builder = scenario()
            .withPlayers("Player", "Opponent")
            .withCardOnBattlefield(1, "Yawgmoth Demon", summoningSickness = false)
            .withLifeTotal(1, 20)
            // Start on the opponent's turn so we transition into player 1's upkeep.
            .withActivePlayer(2)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        if (withArtifact) builder = builder.withCardOnBattlefield(1, "Ornithopter")
        // Library fuel so neither player decks out during step advances.
        repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
        repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
        val game = builder.build()
        game.passUntilPhase(Phase.ENDING, Step.END)
        game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
        game.resolveStack()
        return game
    }

    init {
        context("Yawgmoth Demon upkeep tax") {

            test("with no artifact the cost is unpayable and the tax applies (tap + 2 to controller)") {
                val game = atPlayer1Upkeep(withArtifact = false)
                val demonId = game.findPermanent("Yawgmoth Demon")!!

                withClue("No payable sacrifice cost → no decision is offered") {
                    game.hasPendingDecision() shouldBe false
                }
                withClue("The tax taps Yawgmoth Demon") {
                    isTapped(game, demonId) shouldBe true
                }
                withClue("The tax deals 2 damage to its controller (20 → 18)") {
                    game.getLifeTotal(1) shouldBe 18
                }
            }

            test("sacrificing an artifact pays the cost and avoids the tax") {
                val game = atPlayer1Upkeep(withArtifact = true)
                val demonId = game.findPermanent("Yawgmoth Demon")!!

                withClue("With an artifact, the punisher offers a sacrifice selection") {
                    game.hasPendingDecision() shouldBe true
                }
                val artifactId = game.findPermanent("Ornithopter")!!
                game.selectCards(listOf(artifactId))
                game.resolveStack()

                withClue("The sacrificed artifact should be gone from the battlefield") {
                    game.isOnBattlefield("Ornithopter") shouldBe false
                }
                withClue("Paying the cost spares Yawgmoth Demon — it is NOT tapped") {
                    isTapped(game, demonId) shouldBe false
                }
                withClue("Paying the cost spares the controller — no life loss (stays at 20)") {
                    game.getLifeTotal(1) shouldBe 20
                }
            }

            test("declining to sacrifice an available artifact still triggers the tax") {
                val game = atPlayer1Upkeep(withArtifact = true)
                val demonId = game.findPermanent("Yawgmoth Demon")!!

                withClue("With an artifact, the punisher offers a sacrifice selection") {
                    game.hasPendingDecision() shouldBe true
                }
                // Decline by selecting no artifact to sacrifice.
                game.skipSelection()
                game.resolveStack()

                withClue("Declining keeps the artifact on the battlefield") {
                    game.isOnBattlefield("Ornithopter") shouldBe true
                }
                withClue("Declining the sacrifice taps Yawgmoth Demon") {
                    isTapped(game, demonId) shouldBe true
                }
                withClue("Declining the sacrifice deals 2 damage to its controller (20 → 18)") {
                    game.getLifeTotal(1) shouldBe 18
                }
            }
        }
    }
}
