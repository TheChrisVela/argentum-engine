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
 * Scenario test for Reckless Spite.
 *
 * Reckless Spite ({1}{B}{B}, Instant): "Destroy two target nonblack creatures.
 * You lose 5 life." Canonical printing is Tempest; reprinted in Invasion.
 */
class RecklessSpiteScenarioTest : ScenarioTestBase() {

    init {
        context("Reckless Spite") {

            test("destroys two nonblack creatures and the caster loses 5 life") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Reckless Spite")
                    .withLandsOnBattlefield(1, "Swamp", 3) // {1}{B}{B}
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, red
                    .withCardOnBattlefield(2, "Capashen Unicorn") // 1/2, white
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!
                val unicorn = game.findPermanent("Capashen Unicorn")!!
                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Reckless Spite"
                }

                val castResult = game.execute(
                    CastSpell(
                        playerId,
                        cardId,
                        targets = listOf(ChosenTarget.Permanent(giant), ChosenTarget.Permanent(unicorn))
                    )
                )
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Both targeted nonblack creatures are destroyed") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isOnBattlefield("Capashen Unicorn") shouldBe false
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                    game.isInGraveyard(2, "Capashen Unicorn") shouldBe true
                }
                withClue("Caster loses 5 life: 20 -> 15") {
                    game.getLifeTotal(1) shouldBe 15
                }
            }
        }
    }
}
