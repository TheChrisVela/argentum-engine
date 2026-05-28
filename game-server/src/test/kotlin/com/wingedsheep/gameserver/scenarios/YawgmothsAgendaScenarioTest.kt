package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Yawgmoth's Agenda.
 *
 * Card reference:
 * - Yawgmoth's Agenda ({3}{B}{B}): Enchantment
 *   You can't cast more than one spell each turn.
 *   You may play lands and cast spells from your graveyard.
 *   If a card would be put into your graveyard from anywhere, exile it instead.
 *
 * Exercises the new SDK building blocks:
 * - [com.wingedsheep.sdk.scripting.RestrictSpellsCastPerTurn]
 * - [com.wingedsheep.sdk.scripting.MayCastFromGraveyard] (free, lifeCost = 0)
 * The graveyard-redirect reuses the existing RedirectZoneChange replacement effect.
 *
 * "Fruition" ({G} sorcery, gain life per Forest) is used as a clean no-target,
 * no-decision spell that lands in the graveyard after resolving.
 */
class YawgmothsAgendaScenarioTest : ScenarioTestBase() {

    private fun TestGame.isInExile(playerNumber: Int, cardName: String): Boolean {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getExile(playerId).any {
            state.getEntity(it)?.get<CardComponent>()?.name == cardName
        }
    }

    init {
        context("Yawgmoth's Agenda - one spell per turn restriction") {

            test("cannot cast a second spell the same turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Yawgmoth's Agenda")
                    .withCardInHand(1, "Fruition")
                    .withCardInHand(1, "Fruition")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // First spell resolves normally.
                game.castSpell(1, "Fruition").error shouldBe null
                game.resolveStack()

                // Second spell is blocked by the per-turn restriction.
                game.castSpell(1, "Fruition").error shouldNotBe null
            }

            test("without Yawgmoth's Agenda a player may cast two spells in a turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Fruition")
                    .withCardInHand(1, "Fruition")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fruition").error shouldBe null
                game.resolveStack()
                game.castSpell(1, "Fruition").error shouldBe null
            }
        }

        context("Yawgmoth's Agenda - cast spells from graveyard") {

            test("can cast a creature from the graveyard for free") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Yawgmoth's Agenda")
                    .withCardInGraveyard(1, "Llanowar Elves")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellFromGraveyard(1, "Llanowar Elves").error shouldBe null
                game.resolveStack()

                game.isOnBattlefield("Llanowar Elves") shouldBe true
                game.isInGraveyard(1, "Llanowar Elves") shouldBe false
            }

            test("cannot cast from the graveyard without Yawgmoth's Agenda") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInGraveyard(1, "Llanowar Elves")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellFromGraveyard(1, "Llanowar Elves").error shouldNotBe null
            }
        }

        context("Yawgmoth's Agenda - cards are exiled instead of going to the graveyard") {

            test("a resolved spell is exiled instead of going to the graveyard") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Yawgmoth's Agenda")
                    .withCardInHand(1, "Fruition")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Fruition").error shouldBe null
                game.resolveStack()

                game.isInGraveyard(1, "Fruition") shouldBe false
                game.isInExile(1, "Fruition") shouldBe true
            }
        }
    }
}
