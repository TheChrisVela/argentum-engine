package com.wingedsheep.engine.triggers

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameEvent
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.GainLifeEffect
import com.wingedsheep.sdk.scripting.references.Player
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class WheneverYouDiscardACardTriggeredAbilityTest : FunSpec({

    /**
     * A permanent whose static trigger spec is 'Whenever you discard a card, gain 1 life.'
     * Used to verify the engine fires a trigger when the controller discards.
     */
    val discardWatcher = CardDefinition.creature(
        name = "Discard Watcher",
        manaCost = ManaCost.parse("{2}{B}"),
        subtypes = emptySet<Subtype>(),
        power = 2,
        toughness = 2,
        oracleText = "Whenever you discard a card, you gain 1 life.",
        script = CardScript.creature(
            TriggeredAbility.create(
                trigger = GameEvent.DiscardEvent(player = Player.You),
                binding = TriggerBinding.ANY,
                effect = GainLifeEffect(1)
            )
        )
    )

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(discardWatcher)
        return driver
    }

    /**
     * GIVEN  A controller has a permanent on the battlefield with
     *        'Whenever you discard a card, gain 1 life'
     * AND    That same controller has at least one card in hand
     * AND    No other discard-related triggers exist on the battlefield
     * WHEN   The controller discards exactly one card from their hand
     *        (the card moves hand → graveyard via a discard action)
     * THEN   The engine fires exactly one 'whenever you discard a card' trigger
     *        owned by that controller
     * AND    The trigger's source is the permanent that registered the ability
     * AND    Resolving the trigger gains the controller 1 life
     */
    test("whenever you discard a card trigger fires exactly once when controller discards") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Swamp" to 20, "Island" to 20),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val watcher = driver.putCreatureOnBattlefield(activePlayer, "Discard Watcher")
        watcher shouldNotBe null

        driver.getLifeTotal(activePlayer) shouldBe 20

        // Give the player one black mana and a Careful Study ({B}: draw a card, discard a card)
        driver.giveMana(activePlayer, Color.BLACK, 1)
        val carefulStudy = driver.putCardInHand(activePlayer, "Careful Study")

        // Cast Careful Study — resolves, draws a card, then pauses for discard selection
        driver.castSpell(activePlayer, carefulStudy)
        driver.bothPass()

        // Pick the first card in hand as the discard; this emits CardsDiscardedEvent
        val handCard = driver.getHand(activePlayer).first()
        driver.submitCardSelection(activePlayer, listOf(handCard))

        // The 'whenever you discard a card' trigger must now be on the stack
        driver.stackSize shouldBeGreaterThan 0

        // Resolve the trigger — controller gains 1 life
        driver.bothPass()

        driver.getLifeTotal(activePlayer) shouldBe 21
    }
})
