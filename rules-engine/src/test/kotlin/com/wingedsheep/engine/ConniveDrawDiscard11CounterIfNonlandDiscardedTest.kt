package com.wingedsheep.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsDiscardedEvent
import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.CountersAddedEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.effects.ConniveEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

/**
 * BDD test for the Connive mechanic:
 * draw a card, then discard a card; if the discarded card is a nonland,
 * put a +1/+1 counter on the connive source permanent.
 */
class ConniveDrawDiscard11CounterIfNonlandDiscardedTest : FunSpec({

    val conniveAbilityId = AbilityId(UUID.randomUUID().toString())

    val conniveCreature = CardDefinition(
        name = "Connive Creature",
        manaCost = ManaCost.parse("{1}{U}"),
        typeLine = TypeLine.creature(setOf(Subtype("Human"))),
        oracleText = "{T}: Connive. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)",
        creatureStats = CreatureStats(2, 2),
        script = CardScript.permanent(
            ActivatedAbility(
                id = conniveAbilityId,
                cost = AbilityCost.Tap,
                effect = ConniveEffect(target = EffectTarget.Self)
            )
        )
    )

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(conniveCreature))
        return driver
    }

    test("connive draws a card, discards a nonland, and places a +1/+1 counter on the connive source") {
        // GIVEN an active player controls a permanent designated as the Connive source with no +1/+1 counters
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Island" to 30, "Forest" to 30),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // AND the library top card is a known nonland
        val topOfLibraryCard = driver.putCardOnTopOfLibrary(activePlayer, "Grizzly Bears")

        // AND the source permanent is on the battlefield with no +1/+1 counters
        val conniveSource = driver.putCreatureOnBattlefield(activePlayer, "Connive Creature")
        driver.removeSummoningSickness(conniveSource)

        // AND the player's hand contains a nonland card available to discard
        val handNonland = driver.putCardInHand(activePlayer, "Grizzly Bears")
        val handSizeBefore = driver.getHandSize(activePlayer)

        // WHEN a Connive effect targeting the source permanent is queued to resolve
        val activationResult = driver.submit(
            ActivateAbility(
                playerId = activePlayer,
                sourceId = conniveSource,
                abilityId = conniveAbilityId
            )
        )
        activationResult.isSuccess shouldBe true

        // AND the engine resolves the Connive effect (player draws a card)
        driver.bothPass()

        // THEN the engine pauses for the player to choose a card to discard
        driver.isPaused shouldBe true
        val decision = driver.pendingDecision as SelectCardsDecision

        // AND the player selects a nonland card from hand to discard
        driver.submitCardSelection(activePlayer, listOf(handNonland))

        // THEN execution completes — no further pauses
        driver.isPaused shouldBe false

        // THEN hand size reflects exactly one net draw with one nonland card moved to graveyard
        driver.getHandSize(activePlayer) shouldBe handSizeBefore  // drew 1, discarded 1 → net 0
        driver.getHand(activePlayer) shouldContain topOfLibraryCard

        // AND the discarded nonland is in the graveyard
        val graveyard = driver.getGraveyard(activePlayer)
        graveyard shouldContain handNonland

        // AND events are emitted in order: card drawn → card discarded (nonland) → +1/+1 counter added
        val events = driver.events
        val drawIdx = events.indexOfFirst { it is CardsDrawnEvent }
        val discardIdx = events.indexOfFirst { it is CardsDiscardedEvent }
        val counterIdx = events.indexOfFirst { it is CountersAddedEvent }

        drawIdx shouldNotBe -1
        discardIdx shouldNotBe -1
        counterIdx shouldNotBe -1

        (drawIdx < discardIdx) shouldBe true
        (discardIdx < counterIdx) shouldBe true

        // AND exactly one +1/+1 counter is placed on the connive source permanent
        val counterEvent = events.filterIsInstance<CountersAddedEvent>().first()
        counterEvent.entityId shouldBe conniveSource
        counterEvent.counterType shouldBe Counters.PLUS_ONE_PLUS_ONE
        counterEvent.amount shouldBe 1

        // AND a ZoneChangeEvent for the discarded card (so madness, dredge, and
        // "whenever a card is put into a graveyard from your hand" observers fire)
        val zoneChange = events.filterIsInstance<ZoneChangeEvent>()
            .firstOrNull { it.entityId == handNonland }
        zoneChange shouldNotBe null
        zoneChange!!.fromZone shouldBe Zone.HAND
        zoneChange.toZone shouldBe Zone.GRAVEYARD
    }

    test("connive does not place a +1/+1 counter when the discarded card is a land") {
        // GIVEN an active player controls a connive source and the only card in hand is a land
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Island" to 30, "Forest" to 30),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val topOfLibraryCard = driver.putCardOnTopOfLibrary(activePlayer, "Grizzly Bears")

        val conniveSource = driver.putCreatureOnBattlefield(activePlayer, "Connive Creature")
        driver.removeSummoningSickness(conniveSource)

        val handLand = driver.putCardInHand(activePlayer, "Forest")

        // WHEN connive activates and resolves
        driver.submit(
            ActivateAbility(
                playerId = activePlayer,
                sourceId = conniveSource,
                abilityId = conniveAbilityId
            )
        )
        driver.bothPass()

        // THEN the engine pauses for the discard choice
        driver.isPaused shouldBe true

        // AND the player picks the land
        driver.submitCardSelection(activePlayer, listOf(handLand))
        driver.isPaused shouldBe false

        // THEN the land is in the graveyard
        driver.getGraveyard(activePlayer) shouldContain handLand
        driver.getHand(activePlayer) shouldContain topOfLibraryCard

        // AND draw and discard events fired, in order
        val events = driver.events
        val drawIdx = events.indexOfFirst { it is CardsDrawnEvent }
        val discardIdx = events.indexOfFirst { it is CardsDiscardedEvent }
        drawIdx shouldNotBe -1
        discardIdx shouldNotBe -1
        (drawIdx < discardIdx) shouldBe true

        // AND NO CountersAddedEvent fired — discarding a land must not buff the source
        events.filterIsInstance<CountersAddedEvent>() shouldBe emptyList()

        // AND the source has no +1/+1 counters
        val counters = driver.state.getEntity(conniveSource)?.get<CountersComponent>()
        (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
    }
})
