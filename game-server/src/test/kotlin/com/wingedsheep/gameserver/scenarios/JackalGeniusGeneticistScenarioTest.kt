package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Jackal, Genius Geneticist.
 *
 * Card reference:
 * - Jackal, Genius Geneticist ({G}{U}): Legendary Creature — Human Scientist Villain, 1/1
 *   Trample
 *   Whenever you cast a creature spell with mana value equal to Jackal's power, copy that
 *   spell as a non-legendary token and put a +1/+1 counter on Jackal.
 */
class JackalGeniusGeneticistScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Jackal, Genius Geneticist — card definition") {

            test("casts for {G}{U} and enters as a legendary green-blue 1/1 creature with trample") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Jackal, Genius Geneticist")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Jackal, Genius Geneticist")
                withClue("Casting Jackal, Genius Geneticist should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Jackal, Genius Geneticist should be on the battlefield") {
                    game.isOnBattlefield("Jackal, Genius Geneticist") shouldBe true
                }

                val jackalId = game.findPermanent("Jackal, Genius Geneticist")
                jackalId shouldNotBe null
                val jackal = jackalId!!

                val projected = stateProjector.project(game.state)

                withClue("Jackal should be legendary") {
                    projected.isLegendary(jackal) shouldBe true
                }
                withClue("Jackal should be a creature") {
                    projected.isCreature(jackal) shouldBe true
                }
                withClue("Jackal should have power 1") {
                    projected.getPower(jackal) shouldBe 1
                }
                withClue("Jackal should have toughness 1") {
                    projected.getToughness(jackal) shouldBe 1
                }
                withClue("Jackal should have trample") {
                    projected.hasKeyword(jackal, Keyword.TRAMPLE) shouldBe true
                }
                withClue("Jackal should be green and blue") {
                    projected.getColors(jackal) shouldBe setOf("GREEN", "BLUE")
                }
                withClue("Jackal should have Human subtype") {
                    projected.getSubtypes(jackal).contains("Human") shouldBe true
                }
                withClue("Jackal should have Scientist subtype") {
                    projected.getSubtypes(jackal).contains("Scientist") shouldBe true
                }
                withClue("Jackal should have Villain subtype") {
                    projected.getSubtypes(jackal).contains("Villain") shouldBe true
                }
            }
        }
    }
}
