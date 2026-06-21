package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * DSK batch 3 — Survival and enters-the-battlefield cards.
 *
 *  - Cynical Loner (#89)          — {1}{B} 3/1 Human Survivor. Can't be blocked by Glimmers.
 *      Survival: if tapped at second main, may search library for a card to the graveyard, shuffle.
 *  - Fanatic of the Harrowing (#96) — {3}{B} 2/2 Human Cleric. ETB: each player discards a card;
 *      if you discarded a card this way, draw a card.
 */
class DskBatch3SurvivalEtbScenarioTest : ScenarioTestBase() {

    init {
        context("Cynical Loner — Survival (mill self to graveyard)") {
            test("a tapped Cynical Loner searches a card from library to graveyard at second main") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Cynical Loner", tapped = true)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                val p1 = com.wingedsheep.sdk.model.EntityId.of("player-1")
                // Drive: the Survival ability is a MayEffect → first a YesNo ("you may search"),
                // then a SelectCards library search. Answer yes, then pick the Swamp.
                var guard = 0
                while (!game.isInGraveyard(1, "Swamp") && guard < 30) {
                    val decision = game.state.pendingDecision
                    when (decision) {
                        is com.wingedsheep.engine.core.YesNoDecision -> game.answerYesNo(true)
                        is com.wingedsheep.engine.core.SelectCardsDecision -> {
                            val swampInLib = game.state.getLibrary(p1).first { id ->
                                game.state.getEntity(id)
                                    ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Swamp"
                            }
                            game.selectCards(listOf(swampInLib))
                        }
                        else -> game.resolveStack()
                    }
                    guard++
                }

                withClue("the chosen card is now in the graveyard") {
                    game.isInGraveyard(1, "Swamp") shouldBe true
                }
            }

            test("an untapped Cynical Loner does NOT fire Survival") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Cynical Loner", tapped = false)
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                repeat(5) { if (!game.hasPendingDecision()) game.resolveStack() }

                withClue("no Survival selection — the Loner is untapped") {
                    game.hasPendingDecision() shouldBe false
                    game.isInGraveyard(1, "Swamp") shouldBe false
                }
            }
        }

        context("Fanatic of the Harrowing — ETB discard then draw") {
            test("each player discards a card and the controller draws back when they discarded") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fanatic of the Harrowing")
                    .withCardInHand(1, "Swamp")           // controller's discard fodder
                    .withCardInHand(2, "Forest")          // opponent's discard fodder
                    .withCardInLibrary(1, "Mountain")     // controller's replacement draw
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fanatic of the Harrowing").error shouldBe null
                game.resolveStack()

                // Discards may pause for a card selection per player; auto-resolve single options.
                var guard = 0
                while (game.hasPendingDecision() && guard < 20) {
                    val decision = game.state.pendingDecision
                    if (decision is ChooseTargetsDecision) break
                    // SelectCards decisions: each player has exactly one discardable card here,
                    // so feeding it the only option is deterministic.
                    val p1 = com.wingedsheep.sdk.model.EntityId.of("player-1")
                    val p2 = com.wingedsheep.sdk.model.EntityId.of("player-2")
                    val swamp = game.state.getHand(p1).firstOrNull { id ->
                        game.state.getEntity(id)
                            ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Swamp"
                    }
                    val forest = game.state.getHand(p2).firstOrNull { id ->
                        game.state.getEntity(id)
                            ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Forest"
                    }
                    when {
                        swamp != null -> game.selectCards(listOf(swamp))
                        forest != null -> game.selectCards(listOf(forest))
                        else -> game.resolveStack()
                    }
                    game.resolveStack()
                    guard++
                }

                withClue("controller discarded the Swamp") { game.isInGraveyard(1, "Swamp") shouldBe true }
                withClue("opponent discarded the Forest") { game.isInGraveyard(2, "Forest") shouldBe true }
                withClue("controller drew a replacement (the Mountain)") {
                    game.isInHand(1, "Mountain") shouldBe true
                }
            }
        }
    }
}
