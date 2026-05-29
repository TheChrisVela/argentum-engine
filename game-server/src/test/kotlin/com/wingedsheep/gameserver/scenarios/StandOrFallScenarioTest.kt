package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
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
 * Scenario tests for Stand or Fall.
 *
 *   {3}{R} Enchantment
 *   "At the beginning of combat on your turn, for each defending player, separate all
 *    creatures that player controls into two piles and that player chooses one. Only
 *    creatures in the chosen piles can block this turn."
 *
 * On your combat, for each opponent you (the source's controller) partition their
 * creatures; that player chooses which pile can block, and the other pile can't block
 * this turn.
 */
class StandOrFallScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Stand or Fall") {

            test("on your combat, you split the defender's creatures; the unchosen pile can't block") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Stand or Fall")
                    .withCardOnBattlefield(2, "Glory Seeker")     // defender's 2/2
                    .withCardOnBattlefield(2, "Festering Goblin") // defender's 1/1
                    .withActivePlayer(1) // your turn
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gloryId = game.findPermanent("Glory Seeker").shouldNotBeNull()
                val goblinId = game.findPermanent("Festering Goblin").shouldNotBeNull()

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                // Step 1 — you (the source's controller) separate the defending player's creatures.
                val split = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                withClue("The enchantment's controller separates the defender's creatures") {
                    split.playerId shouldBe game.player1Id
                    split.options.toSet() shouldBe setOf(gloryId, goblinId)
                }
                // Pile 1 = Glory Seeker; Pile 2 = Festering Goblin.
                game.selectCards(listOf(gloryId))

                // Step 2 — the defending player chooses which pile can block.
                val choose = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("The defending player chooses which of their piles can block") {
                    choose.playerId shouldBe game.player2Id
                }
                // Choose Pile 2 (Festering Goblin) as the blockers; Glory Seeker is then restricted.
                game.submitDecision(OptionChosenResponse(choose.id, 1))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Glory Seeker (unchosen pile) can't block this turn") {
                    projected.cantBlock(gloryId) shouldBe true
                }
                withClue("Festering Goblin (chosen pile) can still block") {
                    projected.cantBlock(goblinId) shouldBe false
                }
            }
        }
    }

    @Suppress("unused")
    private fun nameOf(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
}
