package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ltr.cards.RiseOfTheWitchKing
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Rise of the Witch-king (LTR).
 *
 * Symmetric "each player sacrifices a creature of their choice" — both `ForceSacrifice`
 * legs capture a `PermanentSnapshot` and inject it into the underlying `EffectContinuation`
 * (the snapshot-threading hook added in this PR), so the `YouSacrificedThisWay` rider can
 * gate on whether the cast's controller actually sacrificed.
 *
 * Per-player sacrifice with a single creature auto-resolves (no decision pause); with
 * multiple creatures the engine prompts each owner separately.
 */
class RiseOfTheWitchKingTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(RiseOfTheWitchKing))
        return driver
    }

    test("each player auto-sacrifices their only creature; rider reanimates from your graveyard") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), skipMulligans = true)
        val active = driver.activePlayer!!
        val opp = driver.getOpponent(active)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val yourBear = driver.putCreatureOnBattlefield(active, "Grizzly Bears")
        val oppBear = driver.putCreatureOnBattlefield(opp, "Grizzly Bears")
        // Pre-seed a permanent card in your graveyard for the rider to reanimate. Move
        // via ZoneTransitionService so it lands properly in the graveyard zone.
        val reanimatable = driver.putCreatureOnBattlefield(active, "Grizzly Bears")
        val gv = com.wingedsheep.engine.handlers.effects.ZoneTransitionService.moveToZone(
            state = driver.state,
            entityId = reanimatable,
            destinationZone = Zone.GRAVEYARD
        )
        driver.replaceState(gv.state)

        val rise = driver.putCardInHand(active, "Rise of the Witch-king")
        driver.giveMana(active, Color.BLACK, 1)
        driver.giveMana(active, Color.GREEN, 1)
        driver.giveColorlessMana(active, 2)
        // Provide the optional reanimation target at cast time as a graveyard-zone Card
        // target. The conditional rider decides whether to *use* it at resolution, but the
        // target itself is locked in here.
        driver.castSpellWithTargets(
            active,
            rise,
            listOf(ChosenTarget.Card(cardId = reanimatable, ownerId = active, zone = Zone.GRAVEYARD))
        ).isSuccess shouldBe true
        driver.bothPass()
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        // Both bears went to graveyards.
        driver.state.getGraveyard(active).contains(yourBear) shouldBe true
        driver.state.getGraveyard(opp).contains(oppBear) shouldBe true
        // The pre-seeded reanimatable is now on the battlefield.
        driver.state.getBattlefield().contains(reanimatable) shouldBe true
    }
})
