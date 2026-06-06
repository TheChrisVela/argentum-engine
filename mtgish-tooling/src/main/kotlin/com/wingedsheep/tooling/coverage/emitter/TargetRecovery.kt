package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Call
import com.wingedsheep.tooling.coverage.Dsl
import com.wingedsheep.tooling.coverage.Infix
import com.wingedsheep.tooling.coverage.Link
import com.wingedsheep.tooling.coverage.Lit
import com.wingedsheep.tooling.coverage.arg
import com.wingedsheep.tooling.coverage.argWordsTagged
import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.dot
import com.wingedsheep.tooling.coverage.findInteger
import com.wingedsheep.tooling.coverage.firstArgStringTagged
import com.wingedsheep.tooling.coverage.firstColorOf
import com.wingedsheep.tooling.coverage.firstWordAtKey
import com.wingedsheep.tooling.coverage.hasStringValue
import com.wingedsheep.tooling.coverage.hasTag
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.nodesTagged
import com.wingedsheep.tooling.coverage.render
import com.wingedsheep.tooling.coverage.strField
import com.wingedsheep.tooling.coverage.subtypes
import com.wingedsheep.tooling.coverage.wordsAtKey
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Target / filter recovery — reads the mtgish target vocabulary the coverage map discards and rebuilds
 * the Argentum target/filter DSL. A filter we can't faithfully render returns null → the card drops
 * to SCAFFOLD rather than emitting a confidently-wrong target.
 *
 * The recovery builds the typed output [Dsl] tree (a base [Lit] + fluent `.method()` [Link]s as a
 * [com.wingedsheep.tooling.coverage.Chain], `or`-joins as an [Infix]); each public `…Dsl` is a thin
 * `…Expr(…)?.let(::render)` wrapper so callers above still receive the historical String.
 */

/**
 * Single source of truth for the filter-predicate suffixes recovered from the mtgish filter IR. The
 * TargetFilter renderer ([creatureFilterExpr]) and the GameObjectFilter renderer ([gameObjectFilterExpr])
 * read the SAME predicate vocabulary onto two parallel fluent surfaces; defining each predicate's
 * IR→DSL recovery ONCE here keeps them from drifting (the regexes were duplicated, and a fix in one
 * renderer had to be mirrored by hand). Each caller still composes these in its own order — the two
 * surfaces are not identical (TargetFilter has no multi-color form, and the renderers append in
 * different orders) — but the per-predicate recovery now lives in one place.
 *
 * Each predicate returns the fluent [Link] (`.tapped()`, `.powerAtLeast(2)`) it recovers from the parsed
 * filter subtree through the typed [IrQuery][hasTag] accessors (bounded by the node, vs. a `compact()`
 * +substring/regex that scanned the whole flattened blob), or null when the clause is absent.
 */
internal object FilterPredicates {
    /** `.powerAtLeast(N)` for a `PowerIs >= N` clause, else null. */
    fun powerAtLeast(node: JsonElement?): Link? = powerBound(node, "GreaterThanOrEqualTo")?.let { Link("powerAtLeast", listOf(arg("$it"))) }

    /** `.powerAtMost(N)` for a `PowerIs <= N` clause, else null. */
    fun powerAtMost(node: JsonElement?): Link? = powerBound(node, "LessThanOrEqualTo")?.let { Link("powerAtMost", listOf(arg("$it"))) }

    fun tapped(node: JsonElement?): Link? = if (node.hasTag("IsTapped")) Link("tapped") else null
    fun untapped(node: JsonElement?): Link? = if (node.hasTag("IsUntapped")) Link("untapped") else null
    fun attacking(node: JsonElement?): Link? = if (node.hasTag("IsAttacking")) Link("attacking") else null

    /** `.withoutKeyword(Keyword.FLYING)` for a `DoesntHaveAbility Flying` clause, else null. */
    fun withoutFlying(node: JsonElement?): Link? =
        if (jsonContains(node, "_Permanents", "DoesntHaveAbility") && node.hasStringValue("Flying"))
            Link("withoutKeyword", listOf(arg("Keyword.FLYING"))) else null

    /** `.withKeyword(Keyword.FLYING)` for a plain `Flying` clause, else null. */
    fun withFlying(node: JsonElement?): Link? =
        if (node.hasStringValue("Flying")) Link("withKeyword", listOf(arg("Keyword.FLYING"))) else null

    /** The integer bound of a `PowerIs` clause whose `args` is `{ _Comparison: <comparison>, args: Integer }`,
     *  scoped to the matching `PowerIs` node so a power range's two bounds stay distinct. */
    private fun powerBound(node: JsonElement?, comparison: String): Int? =
        node.nodesTagged("PowerIs")
            .firstOrNull { it["args"].strField("_Comparison") == comparison }
            ?.let { findInteger(it["args"]) as? Int }
}

internal fun EmitCtx.creatureFilterDsl(filterNode: JsonElement?): String? = creatureFilterExpr(filterNode)?.let(::render)

internal fun EmitCtx.creatureFilterExpr(filterNode: JsonElement?): Dsl? {
    val blob = compact(filterNode)
    // "nonartifact creature" (the Terror template) renders via .nonartifact(); any OTHER non-cardtype
    // restriction has no faithful filter rendering yet, so drop to SCAFFOLD rather than omit it.
    val nonCardtypes = filterNode.argWordsTagged("IsNonCardtype")
    if (nonCardtypes.any { it != "Artifact" }) return null
    // "target creature you control" / "...an opponent controls" — the controller restriction is a
    // ControlledByAPlayer clause. Preserve it as a `.youControl()` / `.opponentControls()` suffix; never
    // drop it (an unrestricted target would let the spell hit any creature). Only the plain-creature
    // path below can compose it, so the special shapes scaffold when a controller clause is present.
    val controller: Link? = when {
        "ControlledByAPlayer" !in blob -> null
        "\"Opponent\"" in blob -> Link("opponentControls")
        "\"You\"" in blob -> Link("youControl")
        else -> return null
    }
    val hasController = "ControlledByAPlayer" in blob
    // Whole-creature shapes whose helpers live on GameObjectFilter (not TargetFilter), or are a named
    // TargetFilter constant. ONS targets use these in isolation, so render them as the whole filter.
    if ("IsAttacking" in blob && "IsBlocking" in blob) {
        if (hasController) return null
        // "...with flying" composes onto the attacking-or-blocking base (Venomspout Brackus).
        return if ("\"Flying\"" in blob)
            Call("TargetFilter", listOf(arg(Lit("GameObjectFilter.Creature").dot("attackingOrBlocking").dot("withKeyword", arg("Keyword.FLYING")))))
        else Lit("TargetFilter.AttackingOrBlockingCreature")
    }
    if ("IsFaceDown" in blob) {
        if (hasController) return null
        return Call("TargetFilter", listOf(arg(Lit("GameObjectFilter.Creature").dot("faceDown"))))
    }
    // "Goblin creature" / "Elf or Soldier creature": one subtype -> withSubtype; several -> an Or of
    // per-subtype creature filters (matches golden's distributed Or[And[IsCreature, HasSubtype X]…]).
    val subs = filterNode.argWordsTagged("IsCreatureType")
    if (subs.isNotEmpty()) {
        if (hasController) return null
        return Call("TargetFilter", listOf(arg(Infix("or", subs.map { Lit("GameObjectFilter.Creature").dot("withSubtype", arg("\"$it\"")) }))))
    }
    var node: Dsl = Lit("TargetFilter.Creature")
    if ("Artifact" in nonCardtypes) node = node.dot("nonartifact")
    // Color stays inline: TargetFilter has no multi-color form, so the creature target uses the
    // IsColor/IsNonColor-scoped single-color recovery rather than gameObjectFilterDsl's collect-all.
    filterNode.firstColorOf("IsNonColor")?.let { node = node.dot("notColor", arg("Color.${it.uppercase()}")) }
    filterNode.firstColorOf("IsColor")?.let { node = node.dot("withColor", arg("Color.${it.uppercase()}")) }
    FilterPredicates.withoutFlying(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.tapped(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.attacking(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.powerAtMost(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.powerAtLeast(filterNode)?.let { node = node.dot(it) }
    controller?.let { node = node.dot(it) }
    return node
}

private fun targetTypes(args: JsonElement?): Set<String> = args.argWordsTagged("IsCardtype").toSet()

internal fun EmitCtx.targetDsl(tnode: JsonObject, actionContext: List<JsonObject>? = null): String? =
    targetExpr(tnode, actionContext)?.let(::render)

/** Faithful Argentum target DSL node, or null if the filter can't be rendered (-> not AUTO). */
internal fun EmitCtx.targetExpr(tnode: JsonObject, actionContext: List<JsonObject>? = null): Dsl? {
    val ttype = tnode.strField("_Target")
    val args = tnode["args"]
    val countInt = findInteger(tnode)
    if (ttype == "TargetPlayer") {
        return if (jsonContains(tnode, "_Players", "Opponent")) Call("TargetOpponent") else Call("TargetPlayer")
    }
    if (ttype == "AnyTarget" || ttype == "TargetPlayerOrPermanent") {
        val blob = compact(tnode)
        if ("Planeswalker" in blob && "Player" in blob && "Opponent" in blob) return Call("TargetOpponentOrPlaneswalker")
        if ("Planeswalker" in blob && "Player" in blob) return Call("TargetPlayerOrPlaneswalker")
        if ("Planeswalker" in blob && "Creature" in blob) return Call("TargetCreatureOrPlaneswalker")
        if (actionContext != null && actionContext.consumesOnlyTargetPlayer()) return Call("TargetPlayer")
        return Call("AnyTarget")
    }
    if (ttype in setOf("TargetPermanent", "NumberTargetPermanents", "UptoNumberTargetPermanents", "OneOrTwoTargetPermanents")) {
        val types = targetTypes(args)
        val blob = compact(args)
        // A creature-subtype restriction ("target Wall") implies a creature target even with no explicit
        // IsCardtype Creature; route it through the creature filter so the subtype isn't dropped (Tunnel).
        val creatureTarget = types == setOf("Creature") || (types.isEmpty() && "IsCreatureType" in blob)
        if (creatureTarget) {
            val filter = creatureFilterExpr(args) ?: return null
            val parts = mutableListOf(arg("filter", filter))
            if (ttype in setOf("NumberTargetPermanents", "UptoNumberTargetPermanents") && countInt is Int) parts.add(0, arg("count", "$countInt"))
            if (ttype == "OneOrTwoTargetPermanents") { parts.add(0, arg("minCount", "1")); parts.add(0, arg("count", "2")) }
            if (ttype == "UptoNumberTargetPermanents") parts.add(0, arg("optional", "true"))
            return Call("TargetCreature", parts)
        }
        val singleType = mapOf("Land" to "TargetFilter.Land", "Artifact" to "TargetFilter.Artifact", "Enchantment" to "TargetFilter.Enchantment")
        if (types.size == 1 && types.first() in singleType) {
            val parts = mutableListOf(arg("filter", singleType.getValue(types.first())))
            if (ttype in setOf("NumberTargetPermanents", "UptoNumberTargetPermanents") && countInt is Int) parts.add(0, arg("count", "$countInt"))
            if (ttype == "UptoNumberTargetPermanents") parts.add(0, arg("optional", "true"))
            return Call("TargetPermanent", parts)
        }
        if (types.isEmpty() && "IsCardtype" !in blob && "IsCreatureType" !in blob) {
            return Call("TargetPermanent")
        }
        val multiType = mapOf(
            setOf("Creature", "Land") to "TargetFilter.CreatureOrLandPermanent",
            setOf("Creature", "Artifact") to "TargetFilter.CreatureOrArtifact",
            setOf("Creature", "Enchantment") to "TargetFilter.CreatureOrEnchantment",
            setOf("Artifact", "Enchantment") to "TargetFilter.ArtifactOrEnchantment",
        )
        multiType[types]?.let {
            return Call("TargetPermanent", listOf(arg("filter", it)))
        }
        return null  // unusual filters: not rendered yet -> SCAFFOLD
    }
    if (ttype == "TargetSpell") {
        val types = targetTypes(args)
        if (types == setOf("Creature", "Sorcery")) return Call("TargetSpell", listOf(arg("filter", "TargetFilter.CreatureOrSorcerySpellOnStack")))
        if (types == setOf("Instant", "Sorcery")) return Call("TargetSpell", listOf(arg("filter", "TargetFilter.InstantOrSorcerySpellOnStack")))
        if (types == setOf("Creature")) return Call("TargetSpell", listOf(arg("filter", "TargetFilter.CreatureSpellOnStack")))
        if (types.isEmpty()) return Call("TargetSpell")
        return null
    }
    if (ttype == "TargetGraveyardCard") {
        val blob = compact(args)
        val types = targetTypes(args)
        val filt: Dsl = when {
            types.isEmpty() && "IsCardtype" !in blob -> Lit("TargetFilter.CardInGraveyard")
            types == setOf("Creature") ->
                Lit(if ("\"You\"" in blob) "TargetFilter.CreatureInYourGraveyard" else "TargetFilter.CreatureInGraveyard")
            types == setOf("Instant", "Sorcery") -> graveyardFilter("InstantOrSorcery", blob)
            types.size == 1 && types.first() in graveyardSingleTypeFilters -> graveyardFilter(graveyardSingleTypeFilters.getValue(types.first()), blob)
            else -> return null
        }
        return Call("TargetObject", listOf(arg("filter", filt)))
    }
    return null
}

private val graveyardSingleTypeFilters = mapOf(
    "Artifact" to "Artifact",
    "Enchantment" to "Enchantment",
    "Instant" to "Instant",
    "Land" to "Land",
    "Sorcery" to "Sorcery",
)

private fun graveyardFilter(gameObjectFilter: String, blob: String): Dsl {
    val owner: Link? = when {
        "\"You\"" in blob -> Link("ownedByYou")
        "\"Opponent\"" in blob -> Link("ownedByOpponent")
        else -> null
    }
    var base: Dsl = Lit("GameObjectFilter.$gameObjectFilter")
    owner?.let { base = base.dot(it) }
    return Call("TargetFilter", listOf(arg(base), arg("zone", "Zone.GRAVEYARD")))
}

private fun List<JsonObject>.consumesOnlyTargetPlayer(): Boolean {
    val targetPlayer = any { jsonContains(it, "_Player", "Ref_TargetPlayer") }
    val targetPermanent = any { jsonContains(it, "_Permanent", "Ref_TargetPermanent") }
    val targetGraveyardCard = any { jsonContains(it, "_GraveyardCard", "Ref_TargetGraveyardCard") }
    return targetPlayer && !targetPermanent && !targetGraveyardCard
}

/** GroupFilter for mass effects. If we can't preserve the filter, scaffold rather than widen. */
internal fun EmitCtx.groupFilterDsl(filterNode: JsonElement?): String? = groupFilterExpr(filterNode)?.let(::render)

internal fun EmitCtx.groupFilterExpr(filterNode: JsonElement?): Dsl? {
    val filtered = gameObjectFilterExpr(filterNode) ?: return null
    val oracle = oracleText?.lowercase() ?: ""
    val args = mutableListOf(arg(filtered))
    // The IR's `Other(ThisPermanent)` is the authoritative "excludeSelf" signal; the oracle phrasing
    // ("all other" / "each other" / "other ... creatures") is the fallback for shapes without it.
    if (jsonContains(filterNode, "_Permanents", "Other") ||
        "all other" in oracle || "each other" in oracle) args.add(arg("excludeSelf", "true"))
    return Call("GroupFilter", args)
}

internal fun EmitCtx.gameObjectFilterDsl(filterNode: JsonElement?): String? = gameObjectFilterExpr(filterNode)?.let(::render)

internal fun EmitCtx.gameObjectFilterExpr(filterNode: JsonElement?): Dsl? {
    val blob = compact(filterNode)
    val types = targetTypes(filterNode)
    val subs = subtypes(filterNode)
    // Creature subtypes come from IsCreatureType (subtypes() only collects land/card subtypes).
    val creatureSubs = filterNode.argWordsTagged("IsCreatureType")
    var node: Dsl = when {
        subs.isNotEmpty() && ("Land" in types || "IsLandType" in blob || "\"Land\"" in blob) ->
            Lit("GameObjectFilter.Land").dot("withSubtype", arg(subtypeArg(subs[0])))
        // A creature subtype always implies creature, so render Creature.withSubtype even when there's no
        // explicit IsCardtype Creature (the "other Merfolk"/"other Goblins" lord groups) — otherwise the
        // ThisPermanent marker below would wrongly widen it to GameObjectFilter.Permanent.
        creatureSubs.isNotEmpty() ->
            Lit("GameObjectFilter.Creature").dot("withSubtype", arg(subtypeArg(creatureSubs[0])))
        subs.isNotEmpty() && ("Creature" in types || "\"Creature\"" in blob) ->
            Lit("GameObjectFilter.Creature").dot("withSubtype", arg(subtypeArg(subs[0])))
        types == setOf("Creature", "Land") -> Lit("GameObjectFilter.CreatureOrLand")
        types == setOf("Creature", "Artifact") -> Lit("GameObjectFilter.CreatureOrArtifact")
        types == setOf("Creature", "Enchantment") -> Lit("GameObjectFilter.CreatureOrEnchantment")
        types == setOf("Artifact", "Enchantment") -> Lit("GameObjectFilter.ArtifactOrEnchantment")
        "Creature" in types || "\"Creature\"" in blob -> Lit("GameObjectFilter.Creature")
        "Land" in types || "\"Land\"" in blob -> Lit("GameObjectFilter.Land")
        "Artifact" in types || "\"Artifact\"" in blob -> Lit("GameObjectFilter.Artifact")
        "Enchantment" in types || "\"Enchantment\"" in blob -> Lit("GameObjectFilter.Enchantment")
        "Permanent" in blob -> Lit("GameObjectFilter.Permanent")
        else -> return null
    }
    val colors = filterNode.wordsAtKey("_Color").map { it.uppercase() }.distinct()
    if (colors.size > 1 && "\"Or\"" in blob) {
        node = node.dot("withAnyColor", *colors.map { arg("Color.$it") }.toTypedArray())
    } else if (colors.size == 1) {
        node = if (filterNode.hasTag("IsNonColor")) node.dot("notColor", arg("Color.${colors[0]}")) else node.dot("withColor", arg("Color.${colors[0]}"))
    }
    (FilterPredicates.withoutFlying(filterNode) ?: FilterPredicates.withFlying(filterNode))?.let { node = node.dot(it) }
    FilterPredicates.powerAtLeast(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.powerAtMost(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.tapped(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.untapped(filterNode)?.let { node = node.dot(it) }
    FilterPredicates.attacking(filterNode)?.let { node = node.dot(it) }
    if ("\"You\"" in blob) node = node.dot("youControl")
    if ("\"Opponent\"" in blob) node = node.dot("opponentControls")
    return node
}

internal fun EmitCtx.revealedHandFilterDsl(filterNode: JsonElement?): String? = revealedHandFilterExpr(filterNode)?.let(::render)

internal fun EmitCtx.revealedHandFilterExpr(filterNode: JsonElement?): Dsl? {
    val landType = filterNode.firstArgStringTagged("IsLandType")
    val color = filterNode.firstWordAtKey("_Color")
    if (landType == null && color == null) return null
    val parts = mutableListOf<Dsl>()
    if (landType != null) parts.add(Lit("GameObjectFilter.Land").dot("withSubtype", arg(subtypeArg(landType))))
    if (color != null) parts.add(Lit("GameObjectFilter.Any").dot("withColor", arg("Color.${color.uppercase()}")))
    return Infix("or", parts, parenthesized = parts.size > 1)
}

internal fun EmitCtx.landSearchFilterDsl(filterNode: JsonElement?): String = render(landSearchFilterExpr(filterNode))

internal fun EmitCtx.landSearchFilterExpr(filterNode: JsonElement?): Dsl {
    val subs = subtypes(filterNode)
    // Dual-land fetch ("a Swamp or Mountain card") -> Land + Or[HasSubtype…], i.e. withAnySubtype;
    // golden factors IsLand out (unlike the distributed creature-subtype form).
    if (subs.size >= 2) return Lit("GameObjectFilter.Land").dot("withAnySubtype", *subs.map { arg("\"$it\"") }.toTypedArray())
    if (subs.isNotEmpty()) return Lit("GameObjectFilter.Land").dot("withSubtype", arg(subtypeArg(subs[0])))
    val blob = compact(filterNode)
    val oracle = oracleText?.lowercase() ?: ""
    return when {
        "basic land" in oracle || "IsBasicLand" in blob -> Lit("GameObjectFilter.BasicLand")
        "sorcery card" in oracle || "\"Sorcery\"" in blob -> Lit("GameObjectFilter.Sorcery")
        "instant card" in oracle || "\"Instant\"" in blob -> Lit("GameObjectFilter.Instant")
        "\"Land\"" in blob -> Lit("GameObjectFilter.Land")
        "\"Creature\"" in blob || "creature" in oracle -> {
            var out: Dsl = Lit("GameObjectFilter.Creature")
            if ("black creature" in oracle) out = out.dot("withColor", arg("Color.BLACK"))
            else filterNode.firstWordAtKey("_Color")?.let { out = out.dot("withColor", arg("Color.${it.uppercase()}")) }
            if ("tapped creature" in oracle) out = out.dot("tapped")
            if ("attacking" in oracle) out = out.dot("attacking")
            out
        }
        else -> Lit("GameObjectFilter.Any")
    }
}
