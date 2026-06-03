package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Weftwalking — Edge of Eternities mythic enchantment, {4}{U}{U}.
 *
 *   When this enchantment enters, if you cast it, shuffle your hand and graveyard into your
 *   library, then draw seven cards.
 *   The first spell each player casts during each of their turns may be cast without paying
 *   its mana cost.
 *
 * Two new pieces of behaviour are exercised here:
 *
 * 1. **ETB-if-cast** ([Conditions.WasCast]) — the shuffle-and-draw fires only when Weftwalking is
 *    cast, not when it enters via another path (verified by checking the cast path triggers the
 *    library reshuffle and a 7-card draw).
 *
 * 2. **First-spell-of-turn free-cast static** ([MayCastFirstSpellOfTurnWithoutPayingMana]) — the
 *    caster's first spell during each of their own turns gets a `{0}` alternative cost; once the
 *    caster has cast a spell this turn the gate closes.
 */
class WeftwalkingScenarioTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(
            deck = Deck.of("Forest" to 30, "Island" to 30),
            startingLife = 20
        )
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("casting Weftwalking shuffles hand and graveyard into the library and draws seven") {
        val driver = createDriver()
        val player = driver.activePlayer!!

        // Seed the graveyard with three cards and the hand with two extra non-Weftwalking cards
        // so we can verify both zones get shuffled away.
        repeat(3) { driver.putCardInGraveyard(player, "Grizzly Bears") }
        driver.putCardInHand(player, "Grizzly Bears")
        driver.putCardInHand(player, "Grizzly Bears")

        val weftwalking = driver.putCardInHand(player, "Weftwalking")
        driver.giveMana(player, Color.BLUE, 6) // {4}{U}{U}

        val handBefore = driver.state.getZone(ZoneKey(player, Zone.HAND)).size
        val graveyardBefore = driver.state.getZone(ZoneKey(player, Zone.GRAVEYARD)).size
        val libraryBefore = driver.state.getZone(ZoneKey(player, Zone.LIBRARY)).size

        driver.submit(
            CastSpell(player, weftwalking, paymentStrategy = PaymentStrategy.FromPool)
        ).isSuccess shouldBe true
        // Spell resolves on first pair of passes; the ETB trigger goes onto the stack and needs
        // a second pair to resolve.
        driver.bothPass()
        driver.bothPass()

        // After resolution: Weftwalking sits on the battlefield, the rest of the hand and the
        // graveyard have been shuffled into the library, and the controller has drawn seven.
        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)).contains(weftwalking) shouldBe true
        driver.state.getZone(ZoneKey(player, Zone.GRAVEYARD)).size shouldBe 0
        driver.state.getZone(ZoneKey(player, Zone.HAND)).size shouldBe 7

        // Library net change: + (handBefore - 1 [Weftwalking itself stays out]) + graveyardBefore - 7 drawn.
        val libraryAfter = driver.state.getZone(ZoneKey(player, Zone.LIBRARY)).size
        libraryAfter shouldBe libraryBefore + (handBefore - 1) + graveyardBefore - 7
    }

    test("with Weftwalking on the battlefield, first spell may be cast for free via useAlternativeCost") {
        val driver = createDriver()
        val player = driver.activePlayer!!

        driver.putPermanentOnBattlefield(player, "Weftwalking")

        // Pre-cast sanity: the static is detected, and the active player has cast zero spells
        // this turn — the cost calculator should expose the {0} free-cast alternative.
        (driver.state.playerSpellsCastThisTurn[player] ?: 0) shouldBe 0
        val costCalculator = CostCalculator(driver.cardRegistry)
        costCalculator.hasFirstSpellOfTurnFreeCast(driver.state, player) shouldBe true

        // Cast Grizzly Bears using the alternative cost — pool is empty, paid nothing.
        val bears = driver.putCardInHand(player, "Grizzly Bears")
        driver.submit(
            CastSpell(player, bears, useAlternativeCost = true, paymentStrategy = PaymentStrategy.FromPool)
        ).isSuccess shouldBe true
        driver.bothPass()

        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)).contains(bears) shouldBe true
    }

    test("the free-cast gate closes after the first spell — second spell can't useAlternativeCost") {
        val driver = createDriver()
        val player = driver.activePlayer!!

        driver.putPermanentOnBattlefield(player, "Weftwalking")

        val first = driver.putCardInHand(player, "Grizzly Bears")
        driver.submit(
            CastSpell(player, first, useAlternativeCost = true, paymentStrategy = PaymentStrategy.FromPool)
        ).isSuccess shouldBe true
        driver.bothPass()

        // After one spell has resolved this turn, the gate is shut.
        ((driver.state.playerSpellsCastThisTurn[player] ?: 0) >= 1) shouldBe true
        val costCalculator = CostCalculator(driver.cardRegistry)
        costCalculator.hasFirstSpellOfTurnFreeCast(driver.state, player) shouldBe false

        // A second cast attempt with useAlternativeCost = true fails — no alternative is available.
        val second = driver.putCardInHand(player, "Grizzly Bears")
        driver.submit(
            CastSpell(player, second, useAlternativeCost = true, paymentStrategy = PaymentStrategy.FromPool)
        ).isSuccess shouldBe false
    }

    test("legal-action label for the free-cast variant reads 'Cast X' (no empty parens) with manaCostString '{0}'") {
        val driver = createDriver()
        val player = driver.activePlayer!!

        driver.putPermanentOnBattlefield(player, "Weftwalking")
        val bears = driver.putCardInHand(player, "Grizzly Bears")

        val enumerator = LegalActionEnumerator.create(driver.cardRegistry)
        val actions = enumerator.enumerate(driver.state, player, EnumerationMode.FULL)

        val freeCast = actions.firstOrNull { la ->
            la.actionType == "CastWithAlternativeCost" &&
                (la.action as? CastSpell)?.cardId == bears
        }
        freeCast shouldNotBe null
        freeCast!!.description shouldBe "Cast Grizzly Bears"
        freeCast.manaCostString shouldBe "{0}"
    }

    test("free-cast variant is not offered on opponent's turn (gate keys on active player)") {
        val driver = createDriver()
        val controller = driver.activePlayer!!
        val opponent = driver.getOpponent(controller)

        driver.putPermanentOnBattlefield(controller, "Weftwalking")
        driver.putCardInHand(controller, "Grizzly Bears")

        // Active player is still `controller` here — flip to confirm the gate keys to whose
        // turn it actually is. The simplest way is to verify the cost-calculator predicate
        // returns false when queried for a non-active player.
        val costCalculator = CostCalculator(driver.cardRegistry)
        costCalculator.hasFirstSpellOfTurnFreeCast(driver.state, opponent) shouldBe false
    }
})
