package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Substrate tests for the Amass mechanic (CR 701.47): "amass [subtype] N" creates a 0/0 black Army
 * token when you control none, then puts N +1/+1 counters on an Army you control and makes it the
 * subtype. Covers token creation, counter stacking onto the same Army, the multi-Army choice, and
 * the printed "When this creature enters, amass Orcs 2" shape (Dunland Crebain).
 */
class AmassScenarioTest : FunSpec({

    val projector = StateProjector()

    // {0} sorceries that just amass, so the substrate can be exercised deterministically.
    val AmassTwo = card("Amass Two") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Amass Orcs 2."
        spell { effect = Effects.Amass(2) }
    }
    val AmassOne = card("Amass One") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Amass Orcs 1."
        spell { effect = Effects.Amass(1) }
    }

    // Pre-existing Armies for the multi-Army choice. Base 2/2 so they survive state-based actions.
    val ZombieArmyA = card("Zombie Army Alpha") {
        manaCost = "{0}"
        typeLine = "Creature — Zombie Army"
        power = 2; toughness = 2
    }
    val ZombieArmyB = card("Zombie Army Beta") {
        manaCost = "{0}"
        typeLine = "Creature — Zombie Army"
        power = 2; toughness = 2
    }

    // The printed card: {2}{B} 1/1 Bird Horror with flying and "When this creature enters, amass Orcs 2."
    val DunlandCrebain = card("Dunland Crebain") {
        manaCost = "{2}{B}"
        typeLine = "Creature — Bird Horror"
        power = 1; toughness = 1
        keywords(Keyword.FLYING)
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            effect = Effects.Amass(2)
        }
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(AmassTwo, AmassOne, ZombieArmyA, ZombieArmyB, DunlandCrebain))
        return driver
    }

    fun GameTestDriver.armiesControlledBy(player: EntityId): List<EntityId> {
        val projected = projector.project(state)
        return projected.getBattlefieldControlledBy(player)
            .filter { projected.isCreature(it) && projected.hasSubtype(it, "Army") }
    }

    fun GameTestDriver.plusOneCounters(id: EntityId): Int =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    // Cast a {0} amass sorcery and resolve it (no pending decision when you control 0 or 1 Army).
    fun GameTestDriver.amass(player: EntityId, cardName: String) {
        val cardId = putCardInHand(player, cardName)
        castSpell(player, cardId)
        bothPass()
    }

    test("amass with no Army creates a 0/0 black Orc Army token and puts the counters on it") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        val active = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.armiesControlledBy(active).size shouldBe 0

        driver.amass(active, "Amass Two")

        val armies = driver.armiesControlledBy(active)
        armies.size shouldBe 1
        val army = armies.single()

        val projected = projector.project(driver.state)
        projected.hasSubtype(army, "Orc") shouldBe true
        projected.hasSubtype(army, "Army") shouldBe true
        projected.getPower(army) shouldBe 2
        projected.getToughness(army) shouldBe 2
        driver.plusOneCounters(army) shouldBe 2
    }

    test("amassing again grows the same Army and creates no second Army") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        val active = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.amass(active, "Amass Two")
        val army = driver.armiesControlledBy(active).single()

        driver.amass(active, "Amass One")

        driver.armiesControlledBy(active) shouldBe listOf(army)
        driver.plusOneCounters(army) shouldBe 3
        projector.project(driver.state).getPower(army) shouldBe 3
    }

    test("with multiple Armies, the controller chooses which one is amassed (CR 701.47a)") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        val active = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val alpha = driver.putCreatureOnBattlefield(active, "Zombie Army Alpha")
        val beta = driver.putCreatureOnBattlefield(active, "Zombie Army Beta")

        val cardId = driver.putCardInHand(active, "Amass Two")
        driver.castSpell(active, cardId)
        driver.bothPass()

        val decision = driver.pendingDecision
        (decision is SelectCardsDecision) shouldBe true
        decision as SelectCardsDecision
        decision.options.toSet() shouldBe setOf(alpha, beta)
        driver.submitDecision(active, CardsSelectedResponse(decision.id, listOf(alpha)))

        // Alpha received the counters and became an Orc; Beta is untouched.
        driver.plusOneCounters(alpha) shouldBe 2
        projector.project(driver.state).hasSubtype(alpha, "Orc") shouldBe true
        driver.plusOneCounters(beta) shouldBe 0
        projector.project(driver.state).hasSubtype(beta, "Orc") shouldBe false
    }

    test("Dunland Crebain's enter trigger amasses Orcs 2") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40))
        val active = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.giveMana(active, Color.BLACK, 3)
        val crebain = driver.putCardInHand(active, "Dunland Crebain")
        driver.castSpell(active, crebain)
        driver.bothPass() // resolve the creature spell — it enters and its ETB trigger goes on the stack
        driver.bothPass() // resolve the "amass Orcs 2" triggered ability

        driver.findPermanent(active, "Dunland Crebain") shouldNotBe null
        val army = driver.armiesControlledBy(active).single()
        projector.project(driver.state).getPower(army) shouldBe 2
        driver.plusOneCounters(army) shouldBe 2
    }
})
