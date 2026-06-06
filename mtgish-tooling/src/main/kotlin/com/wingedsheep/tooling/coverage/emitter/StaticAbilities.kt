package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.asInt
import com.wingedsheep.tooling.coverage.asStr
import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import com.wingedsheep.tooling.coverage.subtypes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * PermanentRuleEffect → `flags()` / `staticAbility { ability = ... }`. These classes live outside the
 * effects registry, so the capability gate is vacuous for them; the generated static is best-effort
 * and (like every draft) flagged for rules-text review.
 */
internal fun EmitCtx.staticBlock(rule: JsonObject): List<String>? {
    val rules = mutableListOf<JsonObject>()
    fun collect(n: JsonElement?) {
        when (n) {
            is JsonObject -> { if (n.strField("_PermanentRule") != null) rules.add(n); n.values.forEach { collect(it) } }
            is JsonArray -> n.forEach { collect(it) }
            else -> {}
        }
    }
    collect(rule)
    if (rules.isEmpty()) { reasons.add("PermanentRuleEffect"); return null }
    val lines = mutableListOf<String>()
    for (r in rules) {
        val name = r.strField("_PermanentRule")!!
        if (name == "CantBeBlocked") {
            lines.add("    flags(AbilityFlag.CANT_BE_BLOCKED)"); continue
        }
        if (name == "MayChooseNotToUntapDuringUntap") {
            lines.add("    flags(AbilityFlag.MAY_NOT_UNTAP)"); continue
        }
        val dsl = staticAbilityDsl(name, r) ?: run { reasons.add(name); return null }
        lines.addAll(listOf("    staticAbility {", "        ability = $dsl", "    }"))
    }
    return lines
}

/**
 * A static `EachPermanentLayerEffect` "lord" rule -> one `staticAbility { ability = ... }` per static
 * layer effect: AdjustPT -> `ModifyStats(powerBonus, toughnessBonus, filter)`, AddAbility{kw} ->
 * `GrantKeyword(Keyword.X, filter)`. The affected group is a GroupFilter (a fixed creature subtype with
 * excludeSelf for "other …", or `GroupFilter.ChosenSubtypeCreatures()` for "creatures of the chosen
 * type"). Anything we can't render exactly scaffolds.
 */
internal fun EmitCtx.staticLordBlock(rule: JsonObject): List<String>? {
    val args = rule["args"] as? JsonArray
    val layerEffects = (args?.getOrNull(1) as? JsonArray)?.filterIsInstance<JsonObject>()
    if (args == null || layerEffects.isNullOrEmpty()) { reasons.add("EachPermanentLayerEffect"); return null }
    val group = lordGroupFilterDsl(args.getOrNull(0)) ?: run { reasons.add("EachPermanentLayerEffect"); return null }
    val lines = mutableListOf<String>()
    for (le in layerEffects) {
        val ability = when (le.strField("_StaticLayerEffect")) {
            "AdjustPT" -> {
                val pt = le["args"] as? JsonArray ?: return scaffoldLord()
                if (pt.size != 2) return scaffoldLord()
                "ModifyStats(powerBonus = ${pt[0].asInt()}, toughnessBonus = ${pt[1].asInt()}, filter = $group)"
            }
            "AddAbility" -> {
                val kw = keywordOf(le) ?: return scaffoldLord()
                "GrantKeyword(Keyword.$kw, $group)"
            }
            else -> return scaffoldLord()
        }
        lines.addAll(listOf("    staticAbility {", "        ability = $ability", "    }"))
    }
    return lines
}

private fun EmitCtx.scaffoldLord(): List<String>? { reasons.add("EachPermanentLayerEffect"); return null }

/**
 * `EnchantPermanent` -> the card-level `auraTarget = Targets.X` line. The enchant restriction is a
 * cardtype filter ("Enchant creature / land / artifact / enchantment"); anything more specific than a
 * bare cardtype (e.g. "enchant tapped creature") scaffolds rather than emit an inexact restriction.
 */
internal fun EmitCtx.auraTargetBlock(rule: JsonObject): List<String>? {
    val filter = rule["args"] as? JsonObject
    if (filter?.strField("_Permanents") != "IsCardtype") { reasons.add("EnchantPermanent"); return null }
    val target = when (filter["args"].asStr()) {
        "Creature" -> "Targets.Creature"
        "Land" -> "Targets.Land"
        "Artifact" -> "Targets.Artifact"
        "Enchantment" -> "Targets.Enchantment"
        else -> { reasons.add("EnchantPermanent"); return null }
    }
    return listOf("    auraTarget = $target")
}

/**
 * A static `PermanentLayerEffect` whose target is the aura's `HostPermanent` (the enchanted permanent)
 * -> one `staticAbility { ability = ... }` per layer effect, applied to the enchanted permanent (no
 * filter, the aura-static default): AdjustPT -> `ModifyStats(p, t)`, AddAbility{kw} ->
 * `GrantKeyword(Keyword.X)`. A layer effect we can't render exactly scaffolds.
 */
internal fun EmitCtx.staticHostBlock(rule: JsonObject): List<String>? {
    val args = rule["args"] as? JsonArray
    if (args == null || !jsonContains(args.getOrNull(0), "_Permanent", "HostPermanent")) {
        reasons.add("PermanentLayerEffect"); return null
    }
    val layerEffects = (args.getOrNull(1) as? JsonArray)?.filterIsInstance<JsonObject>()
    if (layerEffects.isNullOrEmpty()) { reasons.add("PermanentLayerEffect"); return null }
    val lines = mutableListOf<String>()
    for (le in layerEffects) {
        val ability = when (le.strField("_StaticLayerEffect")) {
            "AdjustPT" -> {
                val pt = le["args"] as? JsonArray
                if (pt?.size != 2) { reasons.add("PermanentLayerEffect"); return null }
                "ModifyStats(${pt[0].asInt()}, ${pt[1].asInt()})"
            }
            "AddAbility" -> {
                val kw = keywordOf(le) ?: run { reasons.add("PermanentLayerEffect"); return null }
                "GrantKeyword(Keyword.$kw)"
            }
            else -> { reasons.add("PermanentLayerEffect"); return null }
        }
        lines.addAll(listOf("    staticAbility {", "        ability = $ability", "    }"))
    }
    return lines
}

/** The affected-group GroupFilter for a lord: chosen-creature-type variable -> the named helper,
 *  otherwise the generic group-filter recovery (fixed subtype, excludeSelf for "other"). */
private fun EmitCtx.lordGroupFilterDsl(filterNode: kotlinx.serialization.json.JsonElement?): String? {
    if (jsonContains(filterNode, "_CreatureTypeVariable", "TheChosenCreatureType") ||
        jsonContains(filterNode, "_Permanents", "IsCreatureTypeVariable")) {
        return "GroupFilter.ChosenSubtypeCreatures()"
    }
    return groupFilterDsl(filterNode)
}

private fun EmitCtx.staticAbilityDsl(ruleName: String, ruleNode: JsonObject): String? {
    when (ruleName) {
        "CantBlock" -> return "CantBlock()"
        "CantBeBlockedByMoreThanOne" -> return "CantBeBlockedByMoreThan(maxBlockers = 1)"
        "CanBlockOnly" -> {
            val kw = keywordOf(ruleNode)
            val bf = if (kw != null) "GameObjectFilter.Creature.withKeyword(Keyword.$kw)" else "GameObjectFilter.Creature"
            return "CanOnlyBlockCreaturesWith(blockerFilter = $bf)"
        }
        "CantBeBlockedExceptByDefenders", "CantBeBlockedByDefenders" -> {
            if (oracleText?.contains("defender", ignoreCase = true) == true) {
                return "CantBeBlockedExceptBy(blockerFilter = GameObjectFilter.Creature.withKeyword(Keyword.DEFENDER))"
            }
            // "can't be blocked except by [creature subtype]" (Invisibility: except by Walls). The rule
            // names the *only* legal blockers, so it must render CantBeBlockedExceptBy with that subtype.
            // Scaffold if we can't recover a single creature subtype — a bare CantBeBlockedBy would
            // invert the meaning (it removes those blockers rather than restricting to them).
            val sub = Regex(""""IsCreatureType",\s*"args":\s*"(\w+)"""").find(compact(ruleNode))?.groupValues?.get(1)
                ?: return null
            return "CantBeBlockedExceptBy(blockerFilter = GameObjectFilter.Creature.withSubtype(${subtypeArg(sub)}))"
        }
        "CantAttackUnlessDefendingPlayer" -> {  // Deep-Sea Serpent: defender must control an Island
            val subs = subtypes(ruleNode)
            if (subs.isEmpty()) return null
            return "CantAttackUnless(Conditions.OpponentControlsLandType(\"${subs[0]}\"))"
        }
        "MustBlockAttacker" -> return "MustBlock()"
        "MustAttackPlayer" -> return "MustAttack()"
    }
    return null
}
