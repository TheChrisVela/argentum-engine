package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.MayPlayFromExileComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * BDD test for the ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurn effect primitive.
 *
 * Scenario: a player has been given a set of exiled cards and may choose one to cast
 * until the end of their next turn. Only the chosen card receives the play permission;
 * the remaining cards in the exile set do not.
 *
 * GIVEN: 3 non-land cards in the controller's exile zone, grouped as an exiled set
 * AND:   the controller designates exactly one of them as the chosen card
 * WHEN:  the effect is executed
 * THEN:  only the chosen card carries MayPlayFromExileComponent scoped to the controller
 * AND:   the permission expires at the cleanup of the controller's next turn
 * AND:   the two unchosen cards carry no MayPlayFromExileComponent at all
 */
class ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurnTest : FunSpec({

    val executor = ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurnExecutor()

    val controllerId = EntityId.generate()
    val opponentId   = EntityId.generate()
    val sourceId     = EntityId.generate()
    val chosenId     = EntityId.generate()
    val unchosen1Id  = EntityId.generate()
    val unchosen2Id  = EntityId.generate()

    fun instantCard(name: String, ownerId: EntityId) = CardComponent(
        cardDefinitionId = name,
        name = name,
        manaCost = ManaCost.parse("{R}"),
        typeLine = TypeLine(cardTypes = setOf(CardType.INSTANT)),
        ownerId = ownerId
    )

    fun buildState(turnNumber: Int = 5): GameState {
        var state = GameState(
            turnNumber  = turnNumber,
            activePlayerId = controllerId,
            turnOrder   = listOf(controllerId, opponentId)
        )
        state = state
            .withEntity(controllerId, ComponentContainer())
            .withEntity(opponentId,   ComponentContainer())
            .withEntity(chosenId,    ComponentContainer()
                .with(instantCard("Lightning Bolt", controllerId))
                .with(OwnerComponent(controllerId)))
            .withEntity(unchosen1Id, ComponentContainer()
                .with(instantCard("Shock", controllerId))
                .with(OwnerComponent(controllerId)))
            .withEntity(unchosen2Id, ComponentContainer()
                .with(instantCard("Incinerate", controllerId))
                .with(OwnerComponent(controllerId)))
        state = state
            .addToZone(ZoneKey(controllerId, Zone.EXILE), chosenId)
            .addToZone(ZoneKey(controllerId, Zone.EXILE), unchosen1Id)
            .addToZone(ZoneKey(controllerId, Zone.EXILE), unchosen2Id)
        return state
    }

    fun context() = EffectContext(
        sourceId     = sourceId,
        controllerId = controllerId,
        opponentId   = opponentId,
        pipeline     = PipelineState(
            storedCollections = mapOf("exiledSet" to listOf(chosenId, unchosen1Id, unchosen2Id))
        )
    )

    test("chosen exiled card is castable until end of controller's next turn then permission expires") {
        // turnNumber = 5, 2-player game; controller is the active player.
        // "Until end of YOUR NEXT turn" → next controller turn is 2 turns away → expiresAfterTurn = 7.
        val state  = buildState(turnNumber = 5)
        val effect = ChooseOneOfTheExiledCardsMayPlayItUntilEndOfNextTurnEffect(
            from   = "exiledSet",
            chosen = chosenId
        )
        val ctx    = context()

        val result = executor.execute(state, effect, ctx)

        result.isSuccess shouldBe true

        // Chosen card receives time-bounded play permission scoped to the controller.
        val chosenPerm = result.newState.getEntity(chosenId)?.get<MayPlayFromExileComponent>()
        chosenPerm shouldNotBe null
        chosenPerm!!.controllerId     shouldBe controllerId
        // For a 2-player game on the controller's own turn, the next controller turn
        // is exactly 2 turns ahead; cleanup of that turn holds the expiry.
        chosenPerm.expiresAfterTurn   shouldBe 7          // 5 + playerCount(2)
        chosenPerm.permanent          shouldBe false

        // Unchosen cards must carry no play permission.
        result.newState.getEntity(unchosen1Id)?.get<MayPlayFromExileComponent>() shouldBe null
        result.newState.getEntity(unchosen2Id)?.get<MayPlayFromExileComponent>() shouldBe null
    }
})
