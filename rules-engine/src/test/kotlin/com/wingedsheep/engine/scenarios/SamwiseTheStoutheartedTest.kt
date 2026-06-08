package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ltr.cards.SamwiseTheStouthearted
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Samwise the Stouthearted (LTR) — ETB target "permanent card in your graveyard that was put
 * there from the battlefield this turn" (Gap 20). Returns the chosen card to hand, then the
 * Ring tempts you.
 */
class SamwiseTheStoutheartedTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(SamwiseTheStouthearted))
        return driver
    }

    test("returns a permanent card put into your graveyard from the battlefield this turn") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true)
        val active = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Stage a permanent in your graveyard that arrived via battlefield this turn —
        // moving via ZoneTransitionService sets the Gap 20 marker.
        val bear = driver.putCreatureOnBattlefield(active, "Grizzly Bears")
        val mv = com.wingedsheep.engine.handlers.effects.ZoneTransitionService.moveToZone(
            state = driver.state,
            entityId = bear,
            destinationZone = Zone.GRAVEYARD
        )
        driver.replaceState(mv.state)

        // Cast Samwise so the ETB triggered ability actually fires.
        val samwise = driver.putCardInHand(active, "Samwise the Stouthearted")
        driver.giveMana(active, Color.WHITE, 1)
        driver.giveColorlessMana(active, 1)
        val handBefore = driver.getHandSize(active)
        driver.castSpell(active, samwise).isSuccess shouldBe true
        driver.bothPass() // resolve Samwise → ETB trigger pauses for target selection

        driver.submitTargetSelection(active, listOf(bear))
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        // Casting Samwise consumed -1 from hand; the ETB returned the bear +1 → net handBefore.
        driver.getHandSize(active) shouldBe handBefore
        driver.state.getGraveyard(active).contains(bear) shouldBe false
    }
})
