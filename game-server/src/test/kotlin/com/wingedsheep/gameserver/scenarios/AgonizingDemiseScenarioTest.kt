package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Agonizing Demise.
 *
 * Agonizing Demise ({3}{B}, Instant, Kicker {1}{R}): Destroy target nonblack creature.
 * It can't be regenerated. If this spell was kicked, Agonizing Demise deals damage equal to
 * that creature's power to the creature's controller.
 */
class AgonizingDemiseScenarioTest : ScenarioTestBase() {

    init {
        context("Agonizing Demise") {

            test("unkicked destroys the creature without dealing damage") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Agonizing Demise")
                    .withLandsOnBattlefield(1, "Swamp", 4) // {3}{B}
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, nonblack
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!

                val castResult = game.castSpell(1, "Agonizing Demise", giant)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant should be destroyed") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
                withClue("Unkicked: opponent takes no damage") {
                    game.getLifeTotal(2) shouldBe 20
                }
            }

            test("kicked destroys the creature and deals power damage to its controller") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Agonizing Demise")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withLandsOnBattlefield(1, "Mountain", 3) // {3}{B} + {1}{R} kicker
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, nonblack
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!
                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Agonizing Demise"
                }

                val castResult = game.execute(
                    CastSpell(
                        playerId,
                        cardId,
                        targets = listOf(ChosenTarget.Permanent(giant)),
                        wasKicked = true
                    )
                )
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant should be destroyed") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
                withClue("Kicked: opponent takes 3 damage (Hill Giant's power): 20 -> 17") {
                    game.getLifeTotal(2) shouldBe 17
                }
            }
        }
    }
}
