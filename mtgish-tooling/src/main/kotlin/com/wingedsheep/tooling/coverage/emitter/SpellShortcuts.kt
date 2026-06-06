package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Call
import com.wingedsheep.tooling.coverage.Dsl
import com.wingedsheep.tooling.coverage.arg
import com.wingedsheep.tooling.coverage.call
import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonObject

/**
 * Whole-card spell shortcuts — multi-action shapes recognised as one named `Patterns.*` rather
 * than rendered action-by-action. [spellBlock] tries each before falling back to the generic path.
 * A `String?` shortcut yields a one-line `effect =`; a `List<String>?` shortcut yields a whole block.
 */
internal fun EmitCtx.eachplayerMaydraw(card: JsonObject): Dsl? {
    val rules = card["Rules"]
    if (!jsonContains(rules, "_Action", "EachPlayerActions") || !jsonContains(rules, "_Action", "DrawUptoNumberCards")) return null
    val blob = compact(rules)
    val mx = Regex(""""DrawUptoNumberCards".*?"args":\s*(\d+)""").find(blob) ?: return null
    val life = Regex(""""GainLifeForEach".*?"args":\s*(\d+)""").find(blob)
    val parts = mutableListOf(arg("maxCards", mx.groupValues[1]))
    if (life != null) parts.add(arg("lifePerCardNotDrawn", life.groupValues[1]))
    return Call("Patterns.Hand.eachPlayerMayDraw", parts)
}

/** Each player discards any number, then draws that many; you draw 1 (Flux). */
internal fun EmitCtx.fluxEffect(card: JsonObject): Dsl? {
    val blob = compact(card["Rules"])
    if ("TheNumberOfCardsDiscardedByPlayerThisWay" in blob && "DiscardAnyNumberOfCards" in blob) {
        val bonus = if ("\"DrawACard\"" in blob) 1 else 0
        return call("Patterns.Hand.eachPlayerDiscardsDraws", arg("controllerBonusDraw", "$bonus"))
    }
    return null
}

/** Each player shuffles their hand into their library, then draws that many (Winds of Change). */
internal fun EmitCtx.windsEffect(card: JsonObject): Dsl? {
    val blob = compact(card["Rules"])
    if ("ShuffleHandIntoLibrary" in blob && "NumCardsShuffledIntoLibraryThisWay" in blob) {
        return call("Patterns.Hand.wheelEffect", arg("Player.Each"))
    }
    return null
}

/** Take an extra turn, then lose at that turn's end step (Last Chance / Final Fortune). */
internal fun EmitCtx.extraTurnEffect(card: JsonObject): Dsl? {
    val (_, actions) = extractEnvelope(card["Rules"])
    if (actions == null) return null
    val hasExtra = actions.any { it.strField("_Action") == "TakeAnExtraTurn" }
    val loseAfter = actions.any { it.strField("_Action") == "CreateFutureTrigger" && jsonContains(it, "_Action", "LoseTheGame") }
    if (hasExtra && loseAfter) return call("TakeExtraTurnEffect", arg("loseAtEndStep", "true"))
    return null
}

/** Forked-Lightning shape: TargetedDistributed -> TargetCreature(count) + DividedDamageEffect. */
internal fun EmitCtx.distributedSpell(card: JsonObject): List<String>? {
    val blob = compact(card["Rules"])
    if ("\"TargetedDistributed\"" !in blob) return null
    val total = Regex(""""DistributeNumberAmongTargets","args":\{"_GameNumber":"Integer","args":(\d+)""").find(blob)
    val mx = Regex(""""BetweenOneAndNumberTargetPermanents","args":\[\{"_GameNumber":"Integer","args":(\d+)""").find(blob)
    if (total == null || mx == null) return null
    val m = mx.groupValues[1]
    return listOf(
        "    spell {",
        "        target = TargetCreature(count = $m, minCount = 1)",
        "        effect = DividedDamageEffect(totalDamage = ${total.groupValues[1]}, minTargets = 1, maxTargets = $m)",
        "    }",
    )
}

/** Draw the difference between target opponent's hand and yours (Balance of Power). */
internal fun EmitCtx.balanceEffect(card: JsonObject): List<String>? {
    val blob = compact(card["Rules"])
    if ("NumCardsInHandIs" in blob && "\"Minus\"" in blob && "TheNumberOfCardsInPlayersHand" in blob) {
        return listOf(
            "    spell {",
            "        target = TargetOpponent()",
            "        effect = DrawCardsEffect(DynamicAmounts.handSizeDifferenceFromTargetOpponent())",
            "    }",
        )
    }
    return null
}
