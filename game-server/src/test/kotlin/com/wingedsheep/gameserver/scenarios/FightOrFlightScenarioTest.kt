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
 * Scenario tests for Fight or Flight.
 *
 *   {3}{W} Enchantment
 *   "At the beginning of combat on each opponent's turn, separate all creatures that
 *    player controls into two piles. Only creatures in the pile of their choice can
 *    attack this turn."
 *
 * You (the enchantment's controller) partition the active opponent's creatures; that
 * player chooses which pile can attack, and the other pile can't attack this turn.
 */
class FightOrFlightScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Fight or Flight") {

            test("on the opponent's combat, you split their creatures; the unchosen pile can't attack") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Fight or Flight")
                    .withCardOnBattlefield(2, "Glory Seeker")     // P2's 2/2
                    .withCardOnBattlefield(2, "Festering Goblin") // P2's 1/1
                    .withActivePlayer(2) // opponent's turn
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gloryId = game.findPermanent("Glory Seeker").shouldNotBeNull()
                val goblinId = game.findPermanent("Festering Goblin").shouldNotBeNull()

                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.resolveStack()

                // Step 1 — you (the enchantment's controller) separate the active player's creatures.
                val split = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<SelectCardsDecision>()
                withClue("The enchantment's controller separates the active opponent's creatures") {
                    split.playerId shouldBe game.player1Id
                    split.options.toSet() shouldBe setOf(gloryId, goblinId)
                }
                // Pile 1 = Glory Seeker; Pile 2 = Festering Goblin.
                game.selectCards(listOf(gloryId))

                // Step 2 — the active player chooses which pile can attack.
                val choose = game.getPendingDecision()
                    .shouldNotBeNull()
                    .shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("The active player — not you — chooses which pile can attack") {
                    choose.playerId shouldBe game.player2Id
                }
                // Choose Pile 2 (Festering Goblin) as the attackers; Glory Seeker is then restricted.
                game.submitDecision(OptionChosenResponse(choose.id, 1))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Glory Seeker (unchosen pile) can't attack this turn") {
                    projected.cantAttack(gloryId) shouldBe true
                }
                withClue("Festering Goblin (chosen pile) can still attack") {
                    projected.cantAttack(goblinId) shouldBe false
                }
            }
        }
    }

    @Suppress("unused")
    private fun nameOf(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
}
