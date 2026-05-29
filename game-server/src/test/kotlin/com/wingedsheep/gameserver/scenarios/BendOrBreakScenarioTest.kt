package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Bend or Break — a per-player "divvy" (CR 700.3) over lands.
 *
 *   {3}{R} Sorcery
 *   "Each player separates all nontoken lands they control into two piles. For each
 *    player, one of their piles is chosen by one of their opponents of their choice.
 *    Destroy all lands in the chosen piles. Tap all lands in the other piles."
 *
 * Each player partitions their own lands; an opponent of that player chooses which
 * pile dies (the other is tapped). Drives both players' iterations to also exercise
 * the per-iteration `Chooser.Opponent` resolution inside ForEachPlayerEffect.
 */
class BendOrBreakScenarioTest : ScenarioTestBase() {

    init {
        context("Bend or Break") {

            test("each player splits their lands; their opponent destroys one pile, the rest are tapped") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Bend or Break")
                    // P1's four Mountains double as the casting mana and the pile fodder.
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    // P2's lands (distinct names so the piles are identifiable).
                    .withCardOnBattlefield(2, "Plains")
                    .withCardOnBattlefield(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Bend or Break").error shouldBe null
                game.resolveStack()

                // Iteration 1: P1 separates their four Mountains.
                val p1Split = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                withClue("The separating player (P1) makes their own partition") {
                    p1Split.playerId shouldBe game.player1Id
                    p1Split.options.size shouldBe 4
                }
                // Pile 1 = two Mountains; Pile 2 = the other two.
                game.selectCards(p1Split.options.take(2))

                // P2 (P1's opponent) chooses which of P1's piles is destroyed.
                val p1Choose = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("An opponent of the separating player chooses the doomed pile") {
                    p1Choose.playerId shouldBe game.player2Id
                }
                game.submitDecision(OptionChosenResponse(p1Choose.id, 0)) // destroy Pile 1 (2 Mountains)

                // Iteration 2: P2 separates their two lands.
                val p2Split = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                withClue("P2 separates their own lands") {
                    p2Split.playerId shouldBe game.player2Id
                    p2Split.options.size shouldBe 2
                }
                val plainsId = p2Split.options.first { nameOf(game, it) == "Plains" }
                game.selectCards(listOf(plainsId)) // Pile 1 = Plains; Pile 2 = Island

                // P1 (P2's opponent) chooses which of P2's piles is destroyed.
                val p2Choose = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("P1 chooses for P2's piles — opponentId is recomputed per iteration") {
                    p2Choose.playerId shouldBe game.player1Id
                }
                game.submitDecision(OptionChosenResponse(p2Choose.id, 0)) // destroy Pile 1 (Plains)
                game.resolveStack()

                withClue("P1: two Mountains destroyed, two remain (tapped)") {
                    game.findPermanents("Mountain").size shouldBe 2
                    game.graveyardSize(1) shouldBe 3 // 2 Mountains + Bend or Break
                    game.findPermanents("Mountain").forEach { id ->
                        game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
                    }
                }
                withClue("P2: Plains destroyed, Island remains and is tapped") {
                    game.isOnBattlefield("Plains") shouldBe false
                    game.isInGraveyard(2, "Plains") shouldBe true
                    game.isOnBattlefield("Island") shouldBe true
                    val islandId = game.findPermanent("Island").shouldNotBeNull()
                    game.state.getEntity(islandId)?.has<TappedComponent>() shouldBe true
                }
            }
        }
    }

    private fun nameOf(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
}
