package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.DamageDealtByPlayersThisTurnComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Grothama, All-Devouring's per-player damage tracker and LTB draw.
 *
 * The granted "fight Grothama" attack trigger relies on existing GrantTriggeredAbility +
 * FightEffect plumbing already covered by other cards (e.g., Hunter Sliver). These tests
 * focus on the new engine support: `DamageDealtByPlayersThisTurnComponent` and
 * `EachPlayerDrawsForDamageDealtToSourceEffect`.
 */
class GrothamaAllDevouringScenarioTest : ScenarioTestBase() {

    init {
        context("Grothama, All-Devouring — damage tracker + LTB draw") {

            test("damage dealt to Grothama is tracked per source-controller player") {
                val game = scenario()
                    .withPlayers("Grothama Player", "Opponent")
                    .withCardOnBattlefield(1, "Grothama, All-Devouring")
                    .withCardInHand(1, "Volcanic Hammer")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val grothamaId = game.findPermanent("Grothama, All-Devouring")
                withClue("Grothama should be on the battlefield") {
                    grothamaId shouldNotBe null
                }

                val castResult = game.castSpell(1, "Volcanic Hammer", targetId = grothamaId)
                withClue("Volcanic Hammer should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val tracker = game.state.getEntity(grothamaId!!)
                    ?.get<DamageDealtByPlayersThisTurnComponent>()
                withClue("Damage tracker should be populated on Grothama after 3 damage from P1") {
                    tracker shouldNotBe null
                    tracker!!.perPlayer[game.player1Id] shouldBe 3
                }
            }

            test("LTB draws cards equal to damage dealt by each player this turn") {
                val game = scenario()
                    .withPlayers("Grothama Player", "Opponent")
                    .withCardOnBattlefield(1, "Grothama, All-Devouring")
                    .withCardInHand(1, "Volcanic Hammer")
                    .withCardInHand(1, "Volcanic Hammer")
                    .withCardInHand(1, "Volcanic Hammer")
                    .withLandsOnBattlefield(1, "Mountain", 6)
                    // Library has 12 cards — Grothama dies after 3 Hammers (9 damage ≥ 8 toughness),
                    // P1 should draw 9 cards.
                    .withCardsInHand(1, "Forest", 0) // no-op sanity
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val grothamaId = game.findPermanent("Grothama, All-Devouring")!!
                val handSizeBefore = game.state.getHand(game.player1Id).size

                game.castSpell(1, "Volcanic Hammer", targetId = grothamaId).also {
                    it.error shouldBe null
                }
                game.resolveStack()
                game.castSpell(1, "Volcanic Hammer", targetId = grothamaId).also {
                    it.error shouldBe null
                }
                game.resolveStack()
                game.castSpell(1, "Volcanic Hammer", targetId = grothamaId).also {
                    it.error shouldBe null
                }
                game.resolveStack()

                // After the third Hammer resolves, Grothama has 9 damage marked ≥ 8 toughness,
                // SBAs send her to graveyard, LTB trigger fires, draw effect runs.
                game.resolveStack()

                withClue("Grothama should no longer be on the battlefield") {
                    game.findPermanent("Grothama, All-Devouring") shouldBe null
                }
                val handSizeAfter = game.state.getHand(game.player1Id).size
                // Hand changes: -3 spells cast, +9 cards drawn ⇒ +6.
                withClue(
                    "P1 should net 9 cards drawn from LTB (3 Hammers dealt 9 damage); " +
                        "hand went from $handSizeBefore → $handSizeAfter"
                ) {
                    handSizeAfter shouldBe handSizeBefore - 3 + 9
                }
            }
        }
    }
}
