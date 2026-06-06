package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.J
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement

/**
 * Pins the shared filter-predicate recovery the two filter renderers ([creatureFilterDsl] /
 * [gameObjectFilterDsl]) compose, so the IR→DSL fragments stay in one place. The predicates now read the
 * parsed filter subtree through the typed [com.wingedsheep.tooling.coverage] IrQuery accessors (the
 * power bound is scoped to its `PowerIs` node), rather than regexing a flattened blob.
 */
class FilterPredicatesTest : StringSpec({

    fun node(json: String): JsonElement = J.parseToJsonElement(json)

    // mtgish encodes a power bound as `PowerIs { _Comparison, args: Integer }` (Fleet-Footed Monk's
    // "creatures with power 2 or greater").
    fun powerIs(comparison: String, n: Int): String =
        """{"_Permanents":"PowerIs","args":{"_Comparison":"$comparison","args":{"_GameNumber":"Integer","args":$n}}}"""

    "power bounds recover from the scoped PowerIs node" {
        val atLeast = node(powerIs("GreaterThanOrEqualTo", 3))
        FilterPredicates.powerAtLeast(atLeast) shouldBe ".powerAtLeast(3)"
        FilterPredicates.powerAtMost(atLeast).shouldBeNull()

        val atMost = node(powerIs("LessThanOrEqualTo", 2))
        FilterPredicates.powerAtMost(atMost) shouldBe ".powerAtMost(2)"
        FilterPredicates.powerAtLeast(atMost).shouldBeNull()
    }

    "a power range keeps its two bounds distinct (scoped per PowerIs node)" {
        // power >= 2 AND power <= 4: each bound must read its OWN PowerIs clause, not the nearest integer.
        val range = node("""[${powerIs("GreaterThanOrEqualTo", 2)},${powerIs("LessThanOrEqualTo", 4)}]""")
        FilterPredicates.powerAtLeast(range) shouldBe ".powerAtLeast(2)"
        FilterPredicates.powerAtMost(range) shouldBe ".powerAtMost(4)"
    }

    "tap / attack state predicates map to their fluent suffixes" {
        FilterPredicates.tapped(node("""{"_Permanents":"IsTapped"}""")) shouldBe ".tapped()"
        FilterPredicates.untapped(node("""{"_Permanents":"IsUntapped"}""")) shouldBe ".untapped()"
        FilterPredicates.attacking(node("""{"_Permanents":"IsAttacking"}""")) shouldBe ".attacking()"
        FilterPredicates.tapped(node("{}")).shouldBeNull()
        // IsUntapped must not be mistaken for IsTapped (substring containment would have).
        FilterPredicates.tapped(node("""{"_Permanents":"IsUntapped"}""")).shouldBeNull()
    }

    "flying recovers as with/without keyword, distinguished by the DoesntHaveAbility marker" {
        val without = node("""{"_Permanents":"DoesntHaveAbility","args":[{"_Keyword":"Flying"}]}""")
        FilterPredicates.withoutFlying(without) shouldBe ".withoutKeyword(Keyword.FLYING)"

        val plain = node("""{"_Permanents":"HasAbility","args":[{"_Keyword":"Flying"}]}""")
        FilterPredicates.withoutFlying(plain).shouldBeNull()
        FilterPredicates.withFlying(plain) shouldBe ".withKeyword(Keyword.FLYING)"
    }
})
