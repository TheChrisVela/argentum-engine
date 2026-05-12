package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Agent Venom.
 *
 * Card reference:
 * - Agent Venom ({2}{B}): 2/3 Legendary Creature — Symbiote Soldier Hero
 *   Flash, Menace
 *   When another nontoken creature you control dies, draw a card and lose 1 life.
 */
class AgentVenomScenarioTest : ScenarioTestBase() {

    init {
        context("Agent Venom — Flash allows casting at instant speed") {
            test("resolves during opponent's end step and enters as 2/3 Legendary Creature with Menace and Flash") {
                // Opponent (player 2) is the active player; it is their end step.
                // Player 1 has priority and 3 untapped Swamps ({B}{B}{B} satisfies {2}{B}).
                // Flash is the only reason this cast is legal outside a main phase.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Agent Venom")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.ENDING, Step.END)
                    .build()

                game.castSpell(1, "Agent Venom")
                game.resolveStack()

                game.isOnBattlefield("Agent Venom") shouldBe true

                val agentVenomId = game.findPermanent("Agent Venom")
                agentVenomId shouldNotBe null

                val clientState = game.getClientState(1)
                val card = clientState.cards[agentVenomId!!]
                card shouldNotBe null

                card!!.power shouldBe 2
                card.toughness shouldBe 3

                card.cardTypes shouldContain "CREATURE"
                card.typeLine.contains("Legendary") shouldBe true

                card.subtypes shouldContain "Symbiote"
                card.subtypes shouldContain "Soldier"
                card.subtypes shouldContain "Hero"

                card.keywords shouldContain Keyword.MENACE
                card.keywords shouldContain Keyword.FLASH
            }
        }

        context("Agent Venom — triggered ability on nontoken creature death") {
            test("draws a card and loses 1 life when another nontoken creature dies") {
                // Player 1 (active) controls Agent Venom and Grizzly Bears.
                // Opponent uses Shock to destroy Grizzly Bears during player 1's main phase.
                // Agent Venom's triggered ability should fire: draw 1, lose 1 life.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Agent Venom")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val grizzlyBearsId = game.findPermanent("Grizzly Bears")!!

                game.castSpell(2, "Shock", grizzlyBearsId)
                game.resolveStack()

                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.getLifeTotal(1) shouldBe 19
                game.handSize(1) shouldBe 1
            }
        }

        context("Agent Venom — triggered ability does NOT fire on token creature death") {
            test("token creature death does not trigger draw or life loss") {
                // Player 1 controls Agent Venom and a creature token (Grizzly Bears with TokenComponent).
                // Opponent kills the token. Agent Venom's trigger should NOT fire — tokens are excluded.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Agent Venom")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Mark Grizzly Bears as a token so Agent Venom's nontoken filter excludes it
                val grizzlyBearsId = game.findPermanent("Grizzly Bears")!!
                val tokenContainer = game.state.getEntity(grizzlyBearsId)!!.with(TokenComponent)
                game.state = game.state.withEntity(grizzlyBearsId, tokenContainer)

                game.castSpell(2, "Shock", grizzlyBearsId)
                game.resolveStack()

                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.getLifeTotal(1) shouldBe 20
                game.handSize(1) shouldBe 0
            }
        }

        context("Agent Venom — triggered ability does NOT fire on its own death") {
            test("Agent Venom dying does not trigger its own ability") {
                // Player 1 controls only Agent Venom. Opponent destroys it with Wrath of God.
                // The trigger binding is OTHER, so Agent Venom never watches its own death.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Agent Venom")
                    .withCardInHand(2, "Wrath of God")
                    .withLandsOnBattlefield(2, "Plains", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(2, "Wrath of God")
                game.resolveStack()

                game.isOnBattlefield("Agent Venom") shouldBe false
                game.getLifeTotal(1) shouldBe 20
                game.handSize(1) shouldBe 0
            }
        }
    }
}
