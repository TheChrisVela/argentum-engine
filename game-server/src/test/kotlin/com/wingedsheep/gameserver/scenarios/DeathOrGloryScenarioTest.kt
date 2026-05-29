package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Death or Glory — a graveyard "divvy" (CR 700.3).
 *
 *   {4}{W} Sorcery
 *   "Separate all creature cards in your graveyard into two piles. Exile the pile
 *    of an opponent's choice and return the other to the battlefield."
 *
 * You partition your graveyard creature cards; an opponent picks which pile is
 * exiled, and the other pile returns to the battlefield under your control.
 */
class DeathOrGloryScenarioTest : ScenarioTestBase() {

    init {
        context("Death or Glory") {

            test("you split; opponent exiles one pile, the other returns to the battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Death or Glory")
                    .withLandsOnBattlefield(1, "Plains", 5)
                    // Graveyard: two creature cards + a noncreature (must be ignored).
                    .withCardInGraveyard(1, "Glory Seeker")   // 2/2 creature
                    .withCardInGraveyard(1, "Festering Goblin") // 1/1 creature
                    .withCardInGraveyard(1, "Mountain")        // land — not a creature card
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Death or Glory").error shouldBe null
                game.resolveStack()

                // Step 1 — caster separates their creature cards.
                val partition = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                withClue("The caster makes the partition") {
                    partition.playerId shouldBe game.player1Id
                }
                withClue("Only the two creature cards are options — the Mountain is excluded") {
                    partition.options.size shouldBe 2
                    partition.options.mapNotNull { nameOf(game, it) }.toSet() shouldBe
                        setOf("Glory Seeker", "Festering Goblin")
                }

                // Pile 1 = Glory Seeker; Pile 2 = Festering Goblin.
                val pileGlory = partition.options.filter { nameOf(game, it) == "Glory Seeker" }
                game.selectCards(pileGlory)

                // Step 2 — opponent picks which pile is exiled.
                val pickPile = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("The opponent — not the caster — chooses the exiled pile") {
                    pickPile.playerId shouldBe game.player2Id
                }
                // Opponent exiles Pile 1 (Glory Seeker); Festering Goblin returns.
                game.submitDecision(OptionChosenResponse(pickPile.id, 0))
                game.resolveStack()

                val exile = game.state.getZone(ZoneKey(game.player1Id, Zone.EXILE))
                    .mapNotNull { nameOf(game, it) }
                withClue("Chosen pile (Glory Seeker) is exiled") {
                    exile shouldBe listOf("Glory Seeker")
                }
                withClue("Other pile (Festering Goblin) returns to the battlefield under your control") {
                    game.isOnBattlefield("Festering Goblin") shouldBe true
                    val gobId = game.findPermanent("Festering Goblin").shouldNotBeNull()
                    game.state.getZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD)) shouldContainId gobId
                }
                withClue("The noncreature card stays in the graveyard, alongside the spent spell") {
                    game.isInGraveyard(1, "Mountain") shouldBe true
                    game.isInGraveyard(1, "Death or Glory") shouldBe true
                }
            }
        }
    }

    private infix fun List<EntityId>.shouldContainId(id: EntityId) {
        (id in this) shouldBe true
    }

    private fun nameOf(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
}
