package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Exercises the SOI shadowland mechanic on Game Trail, composed from two atoms:
 *  - `OnEnterRunEffect` (generic "as ~ enters, run X" replacement)
 *  - `Effects.MayRevealCardFromHand` (atomic optional reveal with `otherwise` rider)
 *
 * Covers the three player-visible branches:
 *   1. Reveal a matching card → enters untapped.
 *   2. Decline the reveal (empty selection) → `otherwise` fires → enters tapped.
 *   3. Empty hand / no matching card → no prompt at all → `otherwise` fires
 *      immediately → enters tapped.
 */
class GameTrailScenarioTest : ScenarioTestBase() {

    init {
        context("Game Trail (SOI shadowland)") {
            test("revealing a Forest from hand leaves Game Trail untapped") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Game Trail")
                    .withCardInHand(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gameTrailId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Game Trail"
                }

                val playResult = game.execute(PlayLand(game.player1Id, gameTrailId))
                withClue("Playing Game Trail should pause for the may-reveal prompt") {
                    playResult.error shouldBe null
                    playResult.pendingDecision shouldNotBe null
                }
                playResult.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()

                val decision = playResult.pendingDecision as SelectCardsDecision
                withClue("Forest in hand should be the only eligible reveal target") {
                    decision.options.size shouldBe 1
                    decision.minSelections shouldBe 0
                    decision.maxSelections shouldBe 1
                }

                val revealResult = game.selectCards(decision.options)
                withClue("Reveal selection should resolve cleanly") {
                    revealResult.error shouldBe null
                }

                withClue("Game Trail should now be on the battlefield") {
                    game.isOnBattlefield("Game Trail") shouldBe true
                }
                val permanentId = game.findPermanent("Game Trail")!!
                val isTapped = game.state.getEntity(permanentId)?.get<TappedComponent>() != null
                withClue("Revealing a Forest must leave Game Trail untapped") {
                    isTapped shouldBe false
                }
            }

            test("declining the reveal taps Game Trail via the otherwise rider") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Game Trail")
                    .withCardInHand(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gameTrailId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Game Trail"
                }

                game.execute(PlayLand(game.player1Id, gameTrailId))
                game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()

                val skipResult = game.skipSelection()
                withClue("Declining the reveal should resolve cleanly") {
                    skipResult.error shouldBe null
                }

                val permanentId = game.findPermanent("Game Trail")!!
                val isTapped = game.state.getEntity(permanentId)?.get<TappedComponent>() != null
                withClue("Declining the reveal must apply the otherwise rider (Tap Self)") {
                    isTapped shouldBe true
                }
            }

            test("no matching card in hand skips the prompt and taps the land") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Game Trail")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gameTrailId = game.state.getHand(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Game Trail"
                }

                val playResult = game.execute(PlayLand(game.player1Id, gameTrailId))
                withClue("No eligible card → executor must skip the prompt entirely") {
                    playResult.error shouldBe null
                    playResult.pendingDecision shouldBe null
                }

                val permanentId = game.findPermanent("Game Trail")!!
                val isTapped = game.state.getEntity(permanentId)?.get<TappedComponent>() != null
                withClue("Game Trail must enter tapped when no reveal is possible") {
                    isTapped shouldBe true
                }
            }
        }
    }
}
