package com.wingedsheep.tooling.coverage.emitter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

/**
 * Pins the emitter's source-formatting helpers: long-line wrapping (so generated cards match the
 * hand-authored house style) and typed-subtype rendering (so filters use `Subtype.X`, not strings).
 */
class EmitterFormatTest : StringSpec({

    "short lines are left untouched" {
        val line = "        effect = Effects.DrawCards(1)"
        wrapLine(line) shouldContainExactly listOf(line)
    }

    "a long call wraps its top-level args one per line, with trailing-comma-free last arg" {
        val line = "        effect = Effects.ForEachInGroup(GroupFilter(GameObjectFilter.Creature." +
            "withKeyword(Keyword.FLYING)), DealDamageEffect(4, EffectTarget.Self))"
        wrapLine(line) shouldContainExactly listOf(
            "        effect = Effects.ForEachInGroup(",
            "            GroupFilter(GameObjectFilter.Creature.withKeyword(Keyword.FLYING)),",
            "            DealDamageEffect(4, EffectTarget.Self)",
            "        )",
        )
    }

    "commas inside string literals never trigger a split" {
        val line = "        flavorText = \"" + "x".repeat(130) + ", and more\""
        // No code-level parens -> the line is returned unchanged despite the comma in the string.
        wrapLine(line) shouldContainExactly listOf(line)
    }

    "comment / KDoc lines are never wrapped" {
        val kdoc = " * " + "Oracle text that runs very long ".repeat(6)
        wrapLine(kdoc) shouldContainExactly listOf(kdoc)
    }

    "nested over-long args wrap recursively and every emitted line fits the width" {
        val inner = (1..12).joinToString(", ") { "DealDamageEffect($it, EffectTarget.Self)" }
        val line = "        effect = Effects.Composite(Effects.ForEachInGroup(GroupFilter.AllCreatures, " +
            "Effects.Composite($inner)))"
        val out = wrapLine(line, maxWidth = 80)
        out.forEach { it.length shouldBeLessThanOrEqual 80 }
    }

    "subtypeArg uses the typed constant when the SDK names it, else falls back to a string" {
        subtypeArg("Plains") shouldBe "Subtype.PLAINS"          // Subtype.kt: val PLAINS = Subtype("Plains")
        subtypeArg("Zombiefied Whatsit 9000") shouldBe "\"Zombiefied Whatsit 9000\""  // no such constant
    }
})
