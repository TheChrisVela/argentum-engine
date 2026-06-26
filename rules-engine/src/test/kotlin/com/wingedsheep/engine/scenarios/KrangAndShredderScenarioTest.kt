package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.PermanentLeftBattlefieldThisTurnComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Krang & Shredder's "Disappear" end-step ability (TMT).
 *
 * "At the beginning of your end step, if a permanent left the battlefield under your control this
 *  turn, you may cast a card exiled with Krang & Shredder without paying its mana cost."
 *
 * Regression guard: the ability casts exactly ONE card (the player chooses), not the entire
 * accumulated linked-exile pile. The earlier implementation granted a blanket
 * play-from-exile-without-paying permission over the whole pile, letting the controller cast every
 * card exiled with Krang for free that turn. The fix gathers the pile, asks the player to pick one,
 * and grants the free cast on just that card.
 */
class KrangAndShredderScenarioTest : ScenarioTestBase() {

    init {
        context("Krang & Shredder Disappear") {

            test("casts only the chosen card from the linked-exile pile, leaving the rest exiled") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Krang & Shredder")
                    .withCardInExile(2, "Centaur Courser") // two opponent cards "exiled with Krang"
                    .withCardInExile(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val krang = game.findPermanent("Krang & Shredder")!!
                fun exiledId(name: String) = game.state.getExile(game.player2Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == name
                }
                val centaur = exiledId("Centaur Courser")
                val grizzly = exiledId("Grizzly Bears")

                // Seed the linked-exile pile (as if Krang's enter/attack triggers had exiled both) and
                // satisfy "a permanent left the battlefield under your control this turn".
                game.state = game.state
                    .updateEntity(krang) { it.with(LinkedExileComponent(listOf(centaur, grizzly))) }
                    .updateEntity(game.player1Id) { it.with(PermanentLeftBattlefieldThisTurnComponent(count = 1)) }

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                // Disappear is a "you may" — accept it.
                withClue("Disappear asks whether to cast a card") {
                    (game.getPendingDecision() is YesNoDecision) shouldBe true
                }
                game.answerYesNo(true)

                // The player picks ONE card from the exiled pile (not the whole pile).
                val select = game.getPendingDecision()
                withClue("Disappear presents a single-card selection over the exiled pile") {
                    (select is SelectCardsDecision) shouldBe true
                }
                game.selectCards(listOf(centaur))
                game.resolveStack()

                // Disappear grants a free-cast (MayPlayPermission) on ONLY the chosen card. The earlier
                // implementation granted the permission over the whole linked-exile pile.
                val freeCastable = game.state.mayPlayPermissions
                    .filter { it.controllerId == game.player1Id }
                    .flatMap { it.cardIds }
                    .toSet()
                withClue("The chosen card is granted a free cast (permissions=$freeCastable)") {
                    freeCastable shouldContain centaur
                }
                withClue("The other exiled card is NOT granted a free cast — not the whole pile (permissions=$freeCastable)") {
                    freeCastable shouldNotContain grizzly
                }
            }
        }
    }
}
