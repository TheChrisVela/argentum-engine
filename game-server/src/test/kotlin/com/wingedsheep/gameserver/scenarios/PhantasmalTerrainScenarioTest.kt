package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.identity.ChosenLandTypeComponent
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Phantasmal Terrain.
 *
 * Card reference:
 * - Phantasmal Terrain ({U}{U}): Enchantment — Aura
 *   "Enchant land. As this Aura enters, choose a basic land type. Enchanted land is the chosen type."
 *
 * Exercises the new [com.wingedsheep.sdk.scripting.ChoiceType.BASIC_LAND_TYPE] entry choice
 * (writes [ChosenLandTypeComponent]) and the [com.wingedsheep.sdk.scripting.SetEnchantedLandTypeFromChosen]
 * static ability ([com.wingedsheep.engine.mechanics.layers.Modification.SetBasicLandTypesFromChosen],
 * a Layer 4 type-change that REPLACES the land's existing basic land types — Rule 305.7).
 */
class PhantasmalTerrainScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()
    private val manaSolver = ManaSolver(cardRegistry)

    /** Answer the pending [ChooseOptionDecision] by picking [landType]. */
    private fun ScenarioTestBase.TestGame.chooseLandType(landType: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val index = decision.options.indexOf(landType)
        withClue("'$landType' should be among options ${decision.options}") {
            (index >= 0) shouldBe true
        }
        submitDecision(OptionChosenResponse(decision.id, index))
        resolveStack()
    }

    init {
        context("Phantasmal Terrain enchants a land and sets its chosen type") {

            test("enchanted Forest becomes the chosen Island and produces blue mana") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Phantasmal Terrain")
                    .withLandsOnBattlefield(1, "Forest", 1)   // the land to enchant
                    .withLandsOnBattlefield(1, "Island", 2)   // mana to pay {U}{U}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val forest = game.findPermanent("Forest")!!
                val castResult = game.castSpell(1, "Phantasmal Terrain", forest)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                // Aura resolves — the EntersWithChoice replacement pauses for the land-type choice.
                game.resolveStack()
                withClue("Should pause for the basic land type choice") {
                    game.hasPendingDecision() shouldBe true
                }
                game.chooseLandType("Island")

                // Chosen type recorded on the aura, which is attached to the Forest.
                val auraId = game.findPermanent("Phantasmal Terrain")!!
                game.state.getEntity(auraId)!!.get<ChosenLandTypeComponent>()
                    .shouldNotBeNull().landType shouldBe "Island"
                game.state.getEntity(auraId)!!.get<AttachedToComponent>()!!.targetId shouldBe forest

                val projected = stateProjector.project(game.state)
                withClue("Enchanted land should now be an Island") {
                    projected.hasSubtype(forest, "Island") shouldBe true
                }
                withClue("Enchanted land should no longer be a Forest (chosen type REPLACES, Rule 305.7)") {
                    projected.hasSubtype(forest, "Forest") shouldBe false
                }
                withClue("Enchanted land should still be a Land") {
                    projected.hasType(forest, "LAND") shouldBe true
                }
                // The two Islands were tapped to pay {U}{U}; the enchanted land (now an Island)
                // is the only untapped source left, so it can pay {U} but no longer {G}.
                withClue("Controller can pay {U} from the now-Island land") {
                    manaSolver.canPay(game.state, game.player1Id, ManaCost.parse("{U}")) shouldBe true
                }
                withClue("Controller can no longer pay {G} (the Forest is gone)") {
                    manaSolver.canPay(game.state, game.player1Id, ManaCost.parse("{G}")) shouldBe false
                }
            }

            test("the chosen type is not hardcoded — choosing Swamp turns a Mountain into a Swamp") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Phantasmal Terrain")
                    .withLandsOnBattlefield(1, "Mountain", 1)  // the land to enchant
                    .withLandsOnBattlefield(1, "Island", 2)    // mana to pay {U}{U}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mountain = game.findPermanent("Mountain")!!
                game.castSpell(1, "Phantasmal Terrain", mountain).error shouldBe null
                game.resolveStack()
                game.chooseLandType("Swamp")

                val projected = stateProjector.project(game.state)
                withClue("Enchanted land should now be a Swamp") {
                    projected.hasSubtype(mountain, "Swamp") shouldBe true
                }
                withClue("Enchanted land should no longer be a Mountain") {
                    projected.hasSubtype(mountain, "Mountain") shouldBe false
                }
                withClue("Controller can pay {B} from the Swamp-ified land") {
                    manaSolver.canPay(game.state, game.player1Id, ManaCost.parse("{B}")) shouldBe true
                }
                withClue("Controller can no longer pay {R} (the Mountain is gone)") {
                    manaSolver.canPay(game.state, game.player1Id, ManaCost.parse("{R}")) shouldBe false
                }
            }
        }
    }
}
