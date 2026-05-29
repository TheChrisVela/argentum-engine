package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
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
 * Scenario tests for Global Ruin.
 *
 *   {4}{W} Sorcery
 *   "Each player chooses from the lands they control a land of each basic land type,
 *    then sacrifices the rest."
 *
 * Each player keeps at most one land of each basic land type (a kept land claims all
 * its basic types) and sacrifices the remainder; a land with no basic land type can't
 * be kept. Exercises the new SelectionRestriction.OnePerBasicLandType inside
 * ForEachPlayerEffect — both the per-type cap and the resumer's rejection of a
 * duplicate type and of a typeless land.
 */
class GlobalRuinScenarioTest : ScenarioTestBase() {

    init {
        context("Global Ruin") {

            test("each player keeps one land per basic type; duplicates and typeless lands are sacrificed") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Global Ruin")
                    // P1's five lands double as casting mana: 2 Plains, 1 Island, 1 Forest, 1 Mountain.
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    // P2: a basic land plus a typeless land that can't be kept.
                    .withCardOnBattlefield(2, "Mountain")
                    .withCardOnBattlefield(2, "Coastal Tower") // "Land" — no basic land type
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Global Ruin").error shouldBe null
                game.resolveStack()

                // Iteration 1: P1 chooses lands to keep.
                val p1Choice = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                withClue("Each player chooses their own lands to keep") {
                    p1Choice.playerId shouldBe game.player1Id
                }
                withClue("The UI is told this is a one-per-basic-land-type selection") {
                    p1Choice.onePerBasicLandType shouldBe true
                }
                withClue("At most one land per distinct basic type (Plains/Island/Forest/Mountain = 4)") {
                    p1Choice.maxSelections shouldBe 4
                }
                // Try to keep both Plains plus Island + Forest (within the cap of 4). The second
                // Plains is rejected as a duplicate type; the Mountain isn't chosen.
                val plains = p1Choice.options.filter { nameOf(game, it) == "Plains" }
                val island = p1Choice.options.first { nameOf(game, it) == "Island" }
                val forest = p1Choice.options.first { nameOf(game, it) == "Forest" }
                game.selectCards(listOf(plains[0], plains[1], island, forest))

                // Iteration 2: P2 chooses — and greedily picks the typeless land.
                val p2Choice = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                p2Choice.playerId shouldBe game.player2Id
                val coastalTower = p2Choice.options.first { nameOf(game, it) == "Coastal Tower" }
                game.selectCards(listOf(coastalTower))
                game.resolveStack()

                withClue("P1 keeps one Plains, the Island and the Forest; the extra Plains and the unchosen Mountain are sacrificed") {
                    game.findPermanents("Plains").size shouldBe 1
                    game.findPermanents("Island").size shouldBe 1
                    game.findPermanents("Forest").size shouldBe 1
                    game.findPermanents("Mountain").any {
                        game.state.getZone(com.wingedsheep.engine.state.ZoneKey(game.player1Id, com.wingedsheep.sdk.core.Zone.BATTLEFIELD)).contains(it)
                    } shouldBe false
                    game.graveyardSize(1) shouldBe 3 // extra Plains + Mountain + Global Ruin
                }
                withClue("P2 can't keep the typeless Coastal Tower even though they chose it; both their lands are sacrificed") {
                    game.isInGraveyard(2, "Coastal Tower") shouldBe true
                    game.isInGraveyard(2, "Mountain") shouldBe true
                    game.graveyardSize(2) shouldBe 2
                }
            }
        }
    }

    private fun nameOf(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
}
