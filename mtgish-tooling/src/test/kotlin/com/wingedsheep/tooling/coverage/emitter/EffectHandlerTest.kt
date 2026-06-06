package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.J
import com.wingedsheep.tooling.coverage.render
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject

/**
 * Per-handler table test for the effect layer: a handful of mtgish `_Action` fragments dispatched
 * through [renderAction] (the ACTION_HANDLERS registry) and rendered, pinned to the exact Argentum
 * Effect DSL. Fast and local; the committed golden remains the exhaustive net.
 */
class EffectHandlerTest : StringSpec({

    val ctx = EmitCtx(emptySet())
    fun obj(json: String): JsonObject = J.parseToJsonElement(json) as JsonObject
    fun effect(json: String): String? = ctx.renderAction(obj(json), null)?.let(::render)

    "constant 1:1 effects render as their fixed token (a Lit)" {
        effect("""{"_Action":"CounterSpell"}""") shouldBe "CounterEffect()"
        effect("""{"_Action":"Shuffle"}""") shouldBe "ShuffleLibraryEffect()"
        effect("""{"_Action":"DiscardACardAtRandom"}""") shouldBe "Patterns.Hand.discardRandom(1)"
    }

    "mana production maps the produce tag to the Add*Mana facade" {
        effect("""{"_Action":"AddMana","args":{"_ManaProduce":"ManaProduceC"}}""") shouldBe "Effects.AddColorlessMana(1)"
        effect("""{"_Action":"AddMana","args":{"_ManaProduce":"ManaProduceR"}}""") shouldBe "Effects.AddMana(Color.RED)"
        effect("""{"_Action":"AddMana","args":{"_ManaProduce":"AnyManaColor"}}""") shouldBe "Effects.AddManaOfChoice()"
    }

    "a mixed mana pool composites inline (single line, not the multi-line Composite)" {
        // And[ManaProduceB, ManaProduceB, ManaProduceB] -> Dark Ritual's {B}{B}{B}.
        val ritual = """{"_Action":"AddMana","args":{"_ManaProduce":"And","args":[""" +
            """{"_ManaProduce":"ManaProduceB"},{"_ManaProduce":"ManaProduceB"},{"_ManaProduce":"ManaProduceB"}]}}"""
        effect(ritual) shouldBe "Effects.AddMana(Color.BLACK, 3)"
    }

    "an unrecognised action declines (-> SCAFFOLD)" {
        effect("""{"_Action":"SomeActionWeDoNotModel"}""").shouldBeNull()
    }
})
