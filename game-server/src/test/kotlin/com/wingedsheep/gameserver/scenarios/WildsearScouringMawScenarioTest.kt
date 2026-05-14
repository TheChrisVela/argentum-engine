package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Wildsear, Scouring Maw (BLC).
 *
 * Enchantment spells you cast from your hand have cascade. When the controller
 * casts an enchantment from hand, the cascade trigger walks the controller's
 * library, exiles cards until a nonland with lower mana value is exiled, lets
 * the controller cast that card for free, and bottoms the rest in random order.
 */
class WildsearScouringMawScenarioTest : ScenarioTestBase() {

    init {
        context("Wildsear, Scouring Maw — cascade on enchantment cast from hand") {

            test("exiles top cards until a nonland with lower mana value, grants free cast, bottoms the rest") {
                val game = scenario()
                    .withPlayers("Wildsear Player", "Opponent")
                    .withCardOnBattlefield(1, "Wildsear, Scouring Maw")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInHand(1, "Hoarder's Overflow")
                    // Library top-down: two Mountains (lands, skipped), then Elvish Pioneer
                    // (mv 1, the cascade hit), then a buffer so the library is non-empty.
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Elvish Pioneer")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    // Buffer card for the opponent so no draw-from-empty triggers fire.
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val librarySizeBefore = game.state.getLibrary(game.player1Id).size

                val castResult = game.castSpell(1, "Hoarder's Overflow")
                withClue("Casting the enchantment should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                // Resolve the cascade trigger, then the enchantment.
                game.resolveStack()

                val exile = game.state.getZone(ZoneKey(game.player1Id, Zone.EXILE))
                val cascadeHit = exile.firstOrNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Elvish Pioneer"
                }

                withClue("Cascade should exile the first nonland with lower mana value (Elvish Pioneer)") {
                    cascadeHit shouldNotBe null
                }

                withClue("Cascade hit should have PlayWithoutPayingCostComponent") {
                    game.state.getEntity(cascadeHit!!)
                        ?.get<PlayWithoutPayingCostComponent>() shouldNotBe null
                }

                withClue("MayPlayPermission should authorize Wildsear's controller to cast the cascade hit") {
                    val perm = game.state.mayPlayPermissions.firstOrNull { cascadeHit in it.cardIds }
                    perm shouldNotBe null
                    perm!!.controllerId shouldBe game.player1Id
                }

                val library = game.state.getLibrary(game.player1Id)
                withClue("Library should retain the two non-hit cards (Forests) plus the skipped Mountains on the bottom") {
                    library.size shouldBeGreaterThanOrEqual 4
                }
                val bottomTwo = library.takeLast(2).map { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name
                }
                withClue("The two skipped Mountains should be on the bottom of the library in some order") {
                    bottomTwo shouldContainAll listOf("Mountain", "Mountain")
                }

                withClue("Net library size should be 4 (started at $librarySizeBefore, removed Elvish Pioneer to exile)") {
                    library shouldHaveSize librarySizeBefore - 1
                }
            }

            test("no cascade trigger when the enchantment is cast from somewhere other than hand") {
                // Sanity check: putting the enchantment directly on the battlefield should
                // NOT trigger Wildsear (since the trigger requires casting from hand).
                val game = scenario()
                    .withPlayers("Wildsear Player", "Opponent")
                    .withCardOnBattlefield(1, "Wildsear, Scouring Maw")
                    .withCardOnBattlefield(1, "Hoarder's Overflow")
                    .withCardInLibrary(1, "Elvish Pioneer")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.resolveStack()

                val exile = game.state.getZone(ZoneKey(game.player1Id, Zone.EXILE))
                withClue("Exile should be empty — no cascade should have triggered") {
                    exile shouldHaveSize 0
                }
            }
        }
    }
}
