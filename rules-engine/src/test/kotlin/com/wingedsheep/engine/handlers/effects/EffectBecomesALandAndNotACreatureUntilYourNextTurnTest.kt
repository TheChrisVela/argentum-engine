package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.BecomesALandAndNotACreatureUntilYourNextTurnEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * BDD scenario: a creature permanent becomes a non-creature land until the
 * controller's next turn.
 *
 * GIVEN  Active player Alice controls a 2/2 creature X with type line 'Creature — Elemental'
 * AND    A BecomesALandAndNotACreatureUntilYourNextTurnEffect is applied to X, with Alice
 *        as the effect controller
 * WHEN   The engine projects the resulting state
 * THEN   projected.isCreature(X) is false
 *        projected.hasType(X, "LAND") is true
 *        projected.hasType(X, "CREATURE") is false
 *        predicateEvaluator.matchesWithProjection for a Land filter returns true
 *        predicateEvaluator.matchesWithProjection for a Creature filter returns false
 * AND    After Alice's next untap step the effect expires:
 *        projected.isCreature(X) is true again, "LAND" type is gone
 */
class EffectBecomesALandAndNotACreatureUntilYourNextTurnTest : FunSpec({

    val projector = StateProjector()
    val predicateEvaluator = PredicateEvaluator()

    // 2/2 Creature — Elemental (the target permanent X in the BDD)
    val elementalX = CardDefinition.creature(
        name = "Test Elemental",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Elemental")),
        power = 2,
        toughness = 2
    )

    // Instant that applies the new effect to a target creature
    val becomesLandSpell = CardDefinition.instant(
        name = "Becomes Land Spell",
        manaCost = ManaCost.parse("{1}{U}"),
        oracleText = "Target creature becomes a land (and isn't a creature) until your next turn.",
        script = CardScript.spell(
            effect = BecomesALandAndNotACreatureUntilYourNextTurnEffect(
                target = EffectTarget.ContextTarget(0)
            ),
            TargetCreature()
        )
    )

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(elementalX, becomesLandSpell))
        return driver
    }

    test("creature X becomes a land (not a creature) until Alice's next untap, then reverts") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            startingLife = 20
        )

        val alice = driver.activePlayer!!
        val opponent = driver.getOpponent(alice)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val spell = driver.putCardInHand(alice, "Becomes Land Spell")
        val creatureX = driver.putCreatureOnBattlefield(alice, "Test Elemental")

        // Sanity: before the effect, X is a creature and not a land
        val before = projector.project(driver.state)
        before.isCreature(creatureX) shouldBe true
        before.hasType(creatureX, "LAND") shouldBe false

        // Alice casts the spell targeting X
        driver.giveMana(alice, Color.BLUE, 2)
        driver.castSpell(alice, spell, targets = listOf(creatureX))
        driver.bothPass()

        val projected = projector.project(driver.state)
        val ctx = PredicateContext(controllerId = alice)

        // Immediately after resolution: X must be a land and must NOT be a creature
        projected.isCreature(creatureX) shouldBe false
        projected.hasType(creatureX, "LAND") shouldBe true
        projected.hasType(creatureX, "CREATURE") shouldBe false

        // Battlefield type-filter queries must use projected state (CLAUDE.md load-bearing rule)
        predicateEvaluator.matchesWithProjection(
            driver.state, projected, creatureX, GameObjectFilter.Land, ctx
        ) shouldBe true
        predicateEvaluator.matchesWithProjection(
            driver.state, projected, creatureX, GameObjectFilter.Creature, ctx
        ) shouldBe false

        // Advance through opponent's turn to Alice's next turn upkeep step.
        // UntilYourNextTurn effects are expired after Alice's untap step.
        driver.passPriorityUntil(Step.UPKEEP, maxPasses = 200)
        driver.activePlayer shouldBe opponent

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN, maxPasses = 200)
        driver.passPriorityUntil(Step.UPKEEP, maxPasses = 200)
        driver.activePlayer shouldBe alice

        // After Alice's untap the effect must have expired
        val afterExpiry = projector.project(driver.state)
        afterExpiry.isCreature(creatureX) shouldBe true
        afterExpiry.hasType(creatureX, "LAND") shouldBe false
        afterExpiry.hasType(creatureX, "CREATURE") shouldBe true
    }
})
