package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Psychic Whorl.
 *
 * Card reference:
 * - Psychic Whorl ({2}{B}): Sorcery. "Target opponent discards two cards.
 *   Then if you control a Rat, surveil 2."
 *
 * Regression: gathering hand cards for the discard pipeline must NOT mark the
 * opponent's hand as revealed to the caster. The opponent chooses what to
 * discard; the caster only sees the cards that actually land in the graveyard.
 */
class PsychicWhorlScenarioTest : ScenarioTestBase() {

    init {
        context("Psychic Whorl") {

            test("does not reveal opponent's remaining hand cards to the caster") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Psychic Whorl")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInHand(2, "Grizzly Bears")
                    .withCardInHand(2, "Hill Giant")
                    .withCardInHand(2, "Shock")
                    .withCardInHand(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findCardsInHand(2, "Grizzly Bears").first()
                val giantId = game.findCardsInHand(2, "Hill Giant").first()
                val shockId = game.findCardsInHand(2, "Shock").first()
                val forestId = game.findCardsInHand(2, "Forest").first()

                val castResult = game.castSpellTargetingPlayer(1, "Psychic Whorl", 2)
                withClue("Psychic Whorl should cast: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val discardDecision = game.getPendingDecision()
                withClue("Opponent should be prompted to choose two cards to discard") {
                    discardDecision shouldNotBe null
                    discardDecision!!.playerId shouldBe game.player2Id
                }

                // Opponent discards Grizzly Bears and Shock. Hill Giant and Forest stay in hand.
                game.selectCards(listOf(bearsId, shockId))

                withClue("Grizzly Bears should land in opponent's graveyard") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                withClue("Shock should land in opponent's graveyard") {
                    game.isInGraveyard(2, "Shock") shouldBe true
                }
                withClue("Opponent should have 2 cards left in hand") {
                    game.handSize(2) shouldBe 2
                }

                // Regression: the cards that stayed in hand must NOT carry a
                // RevealedToComponent that exposes them to the caster.
                val giantRevealed = game.state.getEntity(giantId)?.get<RevealedToComponent>()
                withClue("Hill Giant must not be revealed to anyone after Psychic Whorl") {
                    giantRevealed shouldBe null
                }
                val forestRevealed = game.state.getEntity(forestId)?.get<RevealedToComponent>()
                withClue("Forest must not be revealed to anyone after Psychic Whorl") {
                    forestRevealed shouldBe null
                }

                // And from the caster's masked client view, the opponent's hand
                // should still be opaque — no card details for the survivors.
                val casterView = game.getClientState(1)
                withClue("Caster should not see card details for Hill Giant in opponent's hand") {
                    casterView.cards.containsKey(giantId) shouldBe false
                }
                withClue("Caster should not see card details for Forest in opponent's hand") {
                    casterView.cards.containsKey(forestId) shouldBe false
                }
            }
        }
    }
}
