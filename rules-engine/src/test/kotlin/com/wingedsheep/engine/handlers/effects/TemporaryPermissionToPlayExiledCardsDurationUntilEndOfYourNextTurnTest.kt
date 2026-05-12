package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.legalactions.support.EnumerationTestDriver
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.MayPlayFromExileComponent
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * BDD scenario: play permission scoped to specific exiled cards, bounded by
 * "until end of your next turn".
 *
 * GIVEN  An active player's resolved effect has exiled two cards from their library into exile,
 *        and the engine attached MayPlayFromExileComponent to those entities with
 *        expiresAfterTurn = grantingTurn + playerCount (i.e. the cleanup of the player's next turn)
 * AND    A third card in exile has no permission
 * AND    The current turn is the active player's main phase with the stack empty
 * AND    Both tagged cards are otherwise legal (mana available, timing legal)
 * WHEN   The engine enumerates legal actions on the granting turn (THEN: permission present),
 *        advances through the opponent's full turn to the player's next main phase (THEN: still present),
 *        then advances past the end step of the player's next turn (THEN: permission expired)
 * THEN   On both the granting turn and the player's next turn, a Cast action sourced from
 *        EXILE is offered for each tagged card
 * AND    The untagged exile card never yields an EXILE cast action
 * AND    After the end step of the player's next turn, no EXILE cast action remains for
 *        either tagged card and no MayPlayFromExileComponent remains on those entities
 */
class TemporaryPermissionToPlayExiledCardsDurationUntilEndOfYourNextTurnTest : FunSpec({

    test("play permission is offered on granting turn and player's next turn, then expires after cleanup of next turn with component stripped") {
        val driver = EnumerationTestDriver()
        driver.registerCards(TestCards.all)
        driver.game.initMirrorMatch(
            deck = Deck.of("Mountain" to 40),
            skipMulligans = true
        )

        val p1 = driver.player1
        val p2 = driver.player2
        val game = driver.game

        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        game.state.activePlayerId shouldBe p1

        // "Until end of your next turn": in a 2-player game the next controller turn
        // is exactly 2 turns away, so the permission covers turn N and expires after
        // the cleanup of turn N+2.
        val grantingTurn = game.state.turnNumber
        val expiresAfterTurn = grantingTurn + 2

        // ── Simulate what the handler would do: exile two cards and tag them ──
        val taggedCard1 = game.putCardInHand(p1, "Lightning Bolt")  // instant, {R}
        val taggedCard2 = game.putCardInHand(p1, "Goblin Guide")    // creature, {R}
        val untaggedCard = game.putCardInHand(p1, "Centaur Courser") // no permission

        val handKey  = ZoneKey(p1, Zone.HAND)
        val exileKey = ZoneKey(p1, Zone.EXILE)

        var state = game.state
        state = state.moveToZone(taggedCard1, handKey, exileKey)
        state = state.updateEntity(taggedCard1) { container ->
            container.with(
                MayPlayFromExileComponent(controllerId = p1, expiresAfterTurn = expiresAfterTurn)
            )
        }
        state = state.moveToZone(taggedCard2, handKey, exileKey)
        state = state.updateEntity(taggedCard2) { container ->
            container.with(
                MayPlayFromExileComponent(controllerId = p1, expiresAfterTurn = expiresAfterTurn)
            )
        }
        state = state.moveToZone(untaggedCard, handKey, exileKey)
        game.replaceState(state)

        // Put untapped Mountains on the battlefield so mana is available
        repeat(3) { game.putLandOnBattlefield(p1, "Mountain") }

        // ── GRANTING TURN: both tagged cards appear as affordable EXILE casts ──
        val grantingTurnView = driver.enumerateFor(p1)

        grantingTurnView.castActionsFor("Lightning Bolt")
            .any { it.sourceZone == "EXILE" } shouldBe true
        grantingTurnView.castActionsFor("Goblin Guide")
            .any { it.sourceZone == "EXILE" } shouldBe true
        // Untagged card must not surface an EXILE cast action
        grantingTurnView.castActionsFor("Centaur Courser")
            .none { it.sourceZone == "EXILE" } shouldBe true

        // ── Advance through player1's remaining turn into player2's main phase ──
        game.passPriorityUntil(Step.END)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN) // now on player2's turn
        game.state.activePlayerId shouldBe p2

        // ── Advance through player2's turn to player1's NEXT main phase ──
        game.passPriorityUntil(Step.END)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN) // now on player1's next turn
        game.state.activePlayerId shouldBe p1

        // ── PLAYER1'S NEXT TURN: tagged cards must still appear from EXILE ──
        val nextTurnView = driver.enumerateFor(p1)

        nextTurnView.castActionsFor("Lightning Bolt")
            .any { it.sourceZone == "EXILE" } shouldBe true
        nextTurnView.castActionsFor("Goblin Guide")
            .any { it.sourceZone == "EXILE" } shouldBe true

        // ── Advance past the end step (cleanup) of player1's next turn ──
        game.passPriorityUntil(Step.END)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN) // now on player2's turn after cleanup of p1's next turn
        game.state.activePlayerId shouldBe p2

        // ── AFTER EXPIRY: permission must be gone ──
        // Neither tagged card should appear as an EXILE cast action for player1
        val afterExpiryView = driver.enumerateFor(p1)

        afterExpiryView.castActionsFor("Lightning Bolt")
            .none { it.sourceZone == "EXILE" } shouldBe true
        afterExpiryView.castActionsFor("Goblin Guide")
            .none { it.sourceZone == "EXILE" } shouldBe true

        // The MayPlayFromExileComponent must have been stripped from both entities
        game.state.getEntity(taggedCard1)?.get<MayPlayFromExileComponent>() shouldBe null
        game.state.getEntity(taggedCard2)?.get<MayPlayFromExileComponent>() shouldBe null
    }
})
