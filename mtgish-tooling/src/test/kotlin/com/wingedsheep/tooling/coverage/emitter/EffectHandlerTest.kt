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

    // --- SetPT layer effect ("target creature becomes a P/T until end of turn") -------------------
    fun layer(json: String, tvar: String?): String? = ctx.renderAction(obj(json), tvar)?.let(::render)

    "SetPT sets base P/T, and the end-of-turn case emits an EXPLICIT Duration.EndOfTurn" {
        // SetBasePowerToughnessEffect defaults to Duration.Permanent, so the EOT layer effect must spell
        // the duration out — relying on the default would set the creature's P/T forever.
        layer(
            """{"_Action":"CreatePermanentLayerEffectUntil","args":[{"_Permanent":"Ref_TargetPermanent"},""" +
                """[{"_LayerEffect":"SetPT","args":{"_PT":"PT","args":[5,1]}}],{"_Expiration":"UntilEndOfTurn"}]}""",
            "t",
        ) shouldBe "SetBasePowerToughnessEffect(t, 5, 1, Duration.EndOfTurn)"
    }

    "SetPT carries a non-default expiration verbatim (for as long as it remains tapped)" {
        layer(
            """{"_Action":"CreatePermanentLayerEffectUntil","args":[{"_Permanent":"Ref_TargetPermanent"},""" +
                """[{"_LayerEffect":"SetPT","args":{"_PT":"PT","args":[0,2]}}],""" +
                """{"_Expiration":"ForAsLongAsPermanentRemainsTapped"}]}""",
            "t",
        ) shouldBe "SetBasePowerToughnessEffect(t, 0, 2, Duration.WhileSourceTapped())"
    }

    "the each-permanent SetPT form wraps the set over a group" {
        layer(
            """{"_Action":"CreateEachPermanentLayerEffectUntil","args":[""" +
                """{"_Permanents":"And","args":[{"_Permanents":"IsCardtype","args":"Creature"},""" +
                """{"_Permanents":"ControlledByAPlayer","args":{"_Players":"SinglePlayer","args":{"_Player":"You"}}}]},""" +
                """[{"_LayerEffect":"SetPT","args":{"_PT":"PT","args":[1,1]}}],{"_Expiration":"UntilEndOfTurn"}]}""",
            null,
        ) shouldBe "Effects.ForEachInGroup(GroupFilter(GameObjectFilter.Creature.youControl()), " +
            "SetBasePowerToughnessEffect(EffectTarget.Self, 1, 1, Duration.EndOfTurn))"
    }

    "a SetPT riding an unsupported AddCardtype still scaffolds (no silently-dropped 'becomes an artifact')" {
        // "It becomes a 0/0 artifact creature": AddCardtype isn't rendered here, so the whole card must
        // scaffold rather than emit just the P/T set and drop the type change.
        layer(
            """{"_Action":"CreatePermanentLayerEffectUntil","args":[{"_Permanent":"Ref_TargetPermanent"},""" +
                """[{"_LayerEffect":"SetPT","args":{"_PT":"PT","args":[0,0]}},""" +
                """{"_LayerEffect":"AddCardtype","args":"Artifact"}],{"_Expiration":"UntilEndOfTurn"}]}""",
            "t",
        ).shouldBeNull()
    }
})
