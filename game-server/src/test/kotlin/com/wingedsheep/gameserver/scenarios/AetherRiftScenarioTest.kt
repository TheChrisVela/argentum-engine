package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Aether Rift (Invasion).
 *
 * Aether Rift ({1}{R}{G}, Enchantment):
 *   "At the beginning of your upkeep, discard a card at random. If you discard a creature card
 *    this way, return it from your graveyard to the battlefield unless any player pays 5 life."
 *
 * Composed from the random-discard pipeline + a [Conditions.CollectionContainsMatch] gate +
 * [Effects.UnlessAnyPlayerPays] (PayLife 5). The "any player may pay" loop asks each player in
 * APNAP order; the reanimation only happens if no one pays.
 *
 * Each player's hand holds exactly one card so the random discard is deterministic.
 */
class AetherRiftScenarioTest : ScenarioTestBase() {

    init {
        context("Aether Rift — discard at random, then maybe reanimate") {

            test("no one pays 5 life: the discarded creature returns to the battlefield") {
                val game = scenario()
                    .withPlayers("P1", "P2")
                    .withCardOnBattlefield(1, "Aether Rift")
                    .withCardInHand(1, "Elvish Warrior")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UNTAP)
                    .build()

                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                withClue("the discarded creature triggers the pay-5-life decision (active player first)") {
                    game.hasPendingDecision() shouldBe true
                }
                // Active player (P1) declines.
                game.answerYesNo(false)
                withClue("the opponent is then offered the chance to pay") {
                    game.hasPendingDecision() shouldBe true
                }
                // Opponent (P2) also declines — no one paid.
                game.answerYesNo(false)

                withClue("Elvish Warrior returns from the graveyard to the battlefield") {
                    game.isOnBattlefield("Elvish Warrior") shouldBe true
                }
                withClue("it is no longer in the graveyard") {
                    game.isInGraveyard(1, "Elvish Warrior") shouldBe false
                }
                withClue("no life was paid by either player") {
                    game.getLifeTotal(1) shouldBe 20
                    game.getLifeTotal(2) shouldBe 20
                }
            }

            test("a player pays 5 life: the discarded creature stays in the graveyard") {
                val game = scenario()
                    .withPlayers("P1", "P2")
                    .withCardOnBattlefield(1, "Aether Rift")
                    .withCardInHand(1, "Elvish Warrior")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UNTAP)
                    .build()

                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                // Active player (P1) declines, then the opponent (P2) pays 5 life.
                game.answerYesNo(false)
                game.answerYesNo(true)

                withClue("the opponent paid 5 life (20 -> 15)") {
                    game.getLifeTotal(2) shouldBe 15
                }
                withClue("Elvish Warrior is NOT returned — it stays in the graveyard") {
                    game.isOnBattlefield("Elvish Warrior") shouldBe false
                    game.isInGraveyard(1, "Elvish Warrior") shouldBe true
                }
            }

            test("discarding a non-creature card asks no one to pay and reanimates nothing") {
                val game = scenario()
                    .withPlayers("P1", "P2")
                    .withCardOnBattlefield(1, "Aether Rift")
                    .withCardInHand(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UNTAP)
                    .build()

                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                withClue("no pay decision is presented when the discarded card isn't a creature") {
                    game.hasPendingDecision() shouldBe false
                }
                withClue("the discarded land is in the graveyard, not the battlefield") {
                    game.isInGraveyard(1, "Forest") shouldBe true
                    game.isOnBattlefield("Forest") shouldBe false
                }
                withClue("no life was paid") {
                    game.getLifeTotal(1) shouldBe 20
                    game.getLifeTotal(2) shouldBe 20
                }
            }
        }
    }
}
