package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Impostor Syndrome.
 *
 * Card reference:
 * - Impostor Syndrome ({4}{U}{U}): Enchantment
 *   [Oracle text per Scryfall]
 */
class ImpostorSyndromeScenarioTest : ScenarioTestBase() {

    init {
        context("Impostor Syndrome cast") {

            test("lands on the battlefield as an enchantment when cast paying {4}{U}{U}") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Impostor Syndrome")
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Impostor Syndrome")
                withClue("Casting Impostor Syndrome should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Impostor Syndrome should be on the battlefield") {
                    game.isOnBattlefield("Impostor Syndrome") shouldBe true
                }
                withClue("Impostor Syndrome should not remain in the caster's hand") {
                    game.isInHand(1, "Impostor Syndrome") shouldBe false
                }
                withClue("Caster's mana pool should be empty after paying {4}{U}{U}") {
                    val manaPool = game.state.getEntity(game.player1Id)
                        ?.get<ManaPoolComponent>()
                    manaPool?.isEmpty shouldBe true
                }
                withClue("Impostor Syndrome should be an Enchantment on the battlefield") {
                    val permanent = game.findPermanent("Impostor Syndrome")
                    val cardComponent = game.state.getEntity(permanent!!)?.get<CardComponent>()
                    cardComponent?.typeLine?.isEnchantment shouldBe true
                }
            }
        }
    }
}
