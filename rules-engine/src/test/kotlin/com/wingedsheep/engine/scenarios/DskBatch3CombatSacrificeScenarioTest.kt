package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dsk.cards.PopularEgotist
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * DSK batch 3 — combat-damage modal trigger and sacrifice payoff.
 *
 *  - Silent Hallcreeper (#72)  — {1}{U} 1/1 Enchantment Creature. Can't be blocked.
 *      Whenever it deals combat damage to a player, choose one that hasn't been chosen —
 *      put two +1/+1 counters; draw a card; or become a copy of another target creature you control.
 *  - Popular Egotist (#114)    — {2}{B} 3/2 Human Rogue.
 *      {1}{B}, Sacrifice another creature or enchantment: gains indestructible EOT, tap it.
 *      Whenever you sacrifice a permanent, target opponent loses 1 life and you gain 1 life.
 */
class DskBatch3CombatSacrificeScenarioTest : FunSpec({

    val projector = StateProjector()

    fun GameTestDriver.advanceToPlayer1DeclareAttackers() {
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        var safety = 0
        while (activePlayer != player1 && safety < 50) {
            bothPass()
            passPriorityUntil(Step.DECLARE_ATTACKERS)
            safety++
        }
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        return driver
    }

    test("Silent Hallcreeper — combat damage offers the modal choice; +1/+1 mode adds two counters") {
        val driver = createDriver()
        driver.registerCard(PopularEgotist) // unused here; keeps registration symmetric
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)

        val me = driver.player1
        val opp = driver.player2
        val creeper = driver.putCreatureOnBattlefield(me, "Silent Hallcreeper")
        driver.removeSummoningSickness(creeper)

        driver.advanceToPlayer1DeclareAttackers()
        driver.declareAttackers(me, listOf(creeper), opp)
        driver.bothPass() // to declare blockers
        driver.declareNoBlockers(opp)
        driver.bothPass() // into combat damage; trigger goes on the stack

        // Resolve into the modal ChooseOptionDecision.
        var guard = 0
        while (driver.pendingDecision !is ChooseOptionDecision && guard < 20) {
            driver.bothPass(); guard++
        }
        val choice = driver.pendingDecision as? ChooseOptionDecision
            ?: error("expected ChooseOptionDecision for the modal trigger; got ${driver.pendingDecision}")
        choice.options.size shouldBe 3

        val counterMode = "Put two +1/+1 counters on this creature"
        choice.options shouldContain counterMode
        driver.submitDecision(me, OptionChosenResponse(choice.id, choice.options.indexOf(counterMode)))
        driver.bothPass()

        val counters = driver.state.getEntity(creeper)?.get<CountersComponent>()?.counters ?: emptyMap()
        counters[CounterType.PLUS_ONE_PLUS_ONE] shouldBe 2
        // 1/1 base + two +1/+1 = 3/3
        projector.getProjectedPower(driver.state, creeper) shouldBe 3
        projector.getProjectedToughness(driver.state, creeper) shouldBe 3
    }

    test("Popular Egotist — sacrificing a permanent drains a target opponent for 1") {
        val driver = createDriver()
        driver.registerCard(PopularEgotist)
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val me = driver.activePlayer!!
        val opp = driver.getOpponent(me)
        val egotist = driver.putCreatureOnBattlefield(me, "Popular Egotist")
        driver.removeSummoningSickness(egotist)
        // One fodder creature (NOT another Egotist, so only one drain trigger fires) to sacrifice
        // for the activated ability's "Sacrifice another creature or enchantment" cost.
        val fodder = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
        driver.removeSummoningSickness(fodder)

        val abilityId = PopularEgotist.activatedAbilities.first().id
        driver.giveMana(me, Color.BLACK, 2)

        // Activate {1}{B}, Sacrifice another creature: with one fodder the sacrifice is deterministic.
        // The drain trigger ("whenever you sacrifice a permanent") targets the opponent.
        driver.submit(
            ActivateAbility(
                playerId = me,
                sourceId = egotist,
                abilityId = abilityId,
                targets = emptyList()
            )
        ).isSuccess shouldBe true

        // Resolve until the drain trigger needs a target, then point it at the opponent.
        var guard = 0
        while (driver.state.stack.isNotEmpty() || driver.pendingDecision != null) {
            if (driver.pendingDecision != null) {
                driver.submitTargetSelection(me, listOf(opp))
            } else {
                driver.bothPass()
            }
            if (guard++ > 30) break
        }

        driver.getLifeTotal(opp) shouldBe 19
        driver.getLifeTotal(me) shouldBe 21

        // The Egotist itself is now tapped and indestructible (EOT); the fodder is gone.
        driver.state.getBattlefield().contains(fodder) shouldBe false
    }
})
