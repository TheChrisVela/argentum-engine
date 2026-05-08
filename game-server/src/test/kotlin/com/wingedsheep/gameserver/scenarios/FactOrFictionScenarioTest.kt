package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Fact or Fiction — the canonical "divvy" mechanic (CR 700.3).
 *
 *   {3}{U} Instant
 *   "Reveal the top five cards of your library. An opponent separates those cards
 *    into two piles. Put one pile into your hand and the other into your graveyard."
 *
 * The implementation models the binary partition as a single
 * `SelectFromCollectionEffect` with `Chooser.Opponent` and labelled bins —
 * the opponent's act of selecting "the Graveyard pile" IS the partition.
 */
class FactOrFictionScenarioTest : ScenarioTestBase() {

    init {
        context("Fact or Fiction") {

            test("opponent partitions five cards; selected pile goes to graveyard, rest to hand") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    // Top five of P1's library (top is index 0)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialHand = game.handSize(1) // includes Fact or Fiction itself

                val cast = game.castSpell(1, "Fact or Fiction")
                withClue("Fact or Fiction should cast: ${cast.error}") {
                    cast.error shouldBe null
                }

                game.resolveStack()

                val decision = game.getPendingDecision()
                withClue("Resolution should pause for the opponent's partition decision") {
                    decision.shouldNotBeNull()
                }
                val select = decision.shouldBeInstanceOf<SelectCardsDecision>()
                withClue("The opponent makes the partition (CR 700.3 / FoF rules)") {
                    select.playerId shouldBe game.player2Id
                }
                withClue("All five revealed cards should be options") {
                    select.options.size shouldBe 5
                }
                withClue("Opponent must be able to put zero cards in the graveyard pile (CR 700.3d)") {
                    select.minSelections shouldBe 0
                }
                withClue("Opponent must be able to put all five in the graveyard pile") {
                    select.maxSelections shouldBe 5
                }
                withClue("Bins should be labelled so the opponent knows which side is which") {
                    select.selectedLabel shouldBe "Graveyard"
                    select.remainderLabel shouldBe "Hand"
                }

                // Opponent picks Mountain and Forest for the graveyard pile.
                val graveyardPile = select.options.filter { id -> nameOf(game, id) in setOf("Mountain", "Forest") }
                graveyardPile.size shouldBe 2

                game.selectCards(graveyardPile)

                withClue("Selected pile lands in P1's graveyard") {
                    game.isInGraveyard(1, "Mountain") shouldBe true
                    game.isInGraveyard(1, "Forest") shouldBe true
                    game.graveyardSize(1) shouldBe 3 // Mountain, Forest, FoF itself
                }
                withClue("Remainder pile lands in P1's hand") {
                    game.isInHand(1, "Plains") shouldBe true
                    game.isInHand(1, "Swamp") shouldBe true
                    game.isInHand(1, "Island") shouldBe true
                    // Original hand was [Fact or Fiction]; after cast it left, then 3 cards arrived.
                    game.handSize(1) shouldBe initialHand - 1 + 3
                }
                withClue("Library should be empty (all five top cards consumed)") {
                    game.librarySize(1) shouldBe 0
                }
            }

            test("empty graveyard pile is legal (CR 700.3d) — opponent sends everything to hand") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fact or Fiction").error shouldBe null
                game.resolveStack()

                val select = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                select.minSelections shouldBe 0

                // Opponent picks NO cards for the graveyard pile.
                game.skipSelection()

                withClue("All five revealed cards should be in P1's hand") {
                    listOf("Mountain", "Forest", "Plains", "Swamp", "Island").forEach { name ->
                        game.isInHand(1, name) shouldBe true
                    }
                }
                withClue("Only Fact or Fiction itself should be in the graveyard") {
                    game.graveyardSize(1) shouldBe 1
                    game.isInGraveyard(1, "Fact or Fiction") shouldBe true
                }
            }

            test("empty hand pile is legal — opponent sends everything to graveyard") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fact or Fiction")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fact or Fiction").error shouldBe null
                game.resolveStack()

                val select = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()

                // Opponent puts ALL revealed cards in the graveyard pile.
                game.selectCards(select.options)

                val gyNames = game.state.getGraveyard(game.player1Id).mapNotNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                withClue("All revealed cards plus FoF itself should be in the graveyard") {
                    gyNames shouldContainExactlyInAnyOrder listOf(
                        "Fact or Fiction", "Mountain", "Forest", "Plains", "Swamp", "Island"
                    )
                }
                // Hand had only FoF before cast; nothing came back.
                game.handSize(1) shouldBe 0
            }
        }
    }

    private fun nameOf(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
}
