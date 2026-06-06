package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Call
import com.wingedsheep.tooling.coverage.Composite
import com.wingedsheep.tooling.coverage.Dsl
import com.wingedsheep.tooling.coverage.Lit
import com.wingedsheep.tooling.coverage.Raw
import com.wingedsheep.tooling.coverage.arg
import com.wingedsheep.tooling.coverage.asArr
import com.wingedsheep.tooling.coverage.call
import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.findInteger
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Zone movement: destroy / bounce / reanimate / search / look / mill. Argentum has no leaf
 *  destroy/discard verb — they compose from MoveToZone (single) / MoveCollection (mass). */
internal val zoneHandlers: Map<String, ActionHandler> = actionHandlers {

    on("PutEachPermanentIntoItsOwnersHand") { node, _, _ ->  // bounce each chosen target
        if (jsonContains(node, "_Permanents", "Ref_TargetPermanents")) {
            call("ForEachTargetEffect", arg(call("listOf", arg(call("Effects.Move", arg("EffectTarget.ContextTarget(0)"), arg("Zone.HAND"))))))
        } else null
    }

    on("DestroyPermanent") { _, args, tvar ->
        val tgt = refTarget(args, tvar) ?: return@on null
        call("Effects.Move", arg(Lit(tgt)), arg("Zone.GRAVEYARD"), arg("byDestruction", "true"))
    }
    on("DestroyPermanentNoRegen") { _, args, tvar ->  // "Destroy …. It can't be regenerated." (Terror, Tunnel)
        val tgt = refTarget(args, tvar) ?: return@on null
        call("Effects.Destroy", arg(Lit(tgt)), arg("noRegenerate", "true"))
    }
    on("DestroyEachPermanent", "DestroyEachPermanentNoRegen") { node, args, _ ->
        if (jsonContains(args, "_Permanents", "Ref_TargetPermanents")) {
            val moveArgs = mutableListOf(arg("EffectTarget.ContextTarget(0)"), arg("Zone.GRAVEYARD"), arg("byDestruction", "true"))
            if (node.strField("_Action") == "DestroyEachPermanentNoRegen") moveArgs.add(arg("noRegenerate", "true"))
            return@on call("ForEachTargetEffect", arg(call("listOf", arg(Call("Effects.Move", moveArgs)))))
        }
        if (oracleText?.contains("target", ignoreCase = true) == true) return@on null
        val noregen = if (node.strField("_Action") == "DestroyEachPermanentNoRegen") "true" else "false"
        val filter = groupFilterExpr(args) ?: return@on null
        call(
            "Effects.ForEachInGroup", arg(filter),
            arg(call("Effects.Move", arg("EffectTarget.Self"), arg("Zone.GRAVEYARD"), arg("byDestruction", "true"))),
            arg("noRegenerate", noregen),
        )
    }

    on("PutPermanentIntoItsOwnersHand") { _, args, tvar ->  // bounce
        val tgt = refTarget(args, tvar) ?: return@on null
        call("Effects.Move", arg(Lit(tgt)), arg("Zone.HAND"))
    }

    on("SacrificePermanent") { _, args, _ ->  // "sacrifice ~" (Blistering Firecat's end-step sacrifice)
        if (jsonContains(args, "_Permanent", "ThisPermanent")) Lit("SacrificeSelfEffect") else null
    }

    on("ShuffleGraveyardCardIntoLibrary") { _, args, tvar ->  // e.g. Alabaster Dragon
        val tgt = refTarget(args, tvar) ?: "EffectTarget.Self"
        call("Effects.Move", arg(Lit(tgt)), arg("Zone.LIBRARY"), arg("ZonePlacement.Shuffled"))
    }

    on("Surveil") { _, args, _ ->  // "Surveil N" -> the look-top / keep-or-bin pipeline
        (findInteger(args) as? Int)?.let { call("Patterns.Library.surveil", arg("$it")) }
    }

    on("PutACardFromHandOnBattlefield") { _, args, _ ->  // "you may put a [basic land] card from your hand …"
        val arr = args.asArr ?: return@on null
        val blob = compact(arr.getOrNull(0))
        val filter = when {
            "IsSupertype" in blob && "\"Basic\"" in blob && "\"Land\"" in blob -> "GameObjectFilter.BasicLand"
            "\"Land\"" in blob && "IsSupertype" !in blob && "IsLandType" !in blob -> "GameObjectFilter.Land"
            else -> return@on null
        }
        val entersTapped = "EntersTapped" in compact(arr.getOrNull(1))
        val parts = mutableListOf(arg("filter", filter))
        if (entersTapped) parts.add(arg("entersTapped", "true"))
        Call("Patterns.Hand.putFromHand", parts)
    }

    on("SearchLibrary") { _, args, _ -> renderSearch(args) }
    on("LookAtTheTopNumberCardsOfLibrary", "LookAtTheTopNumberCardsOfPlayersLibrary") { node, args, tvar -> renderLook(node, args, tvar) }

    on("PutGraveyardCardOntoBattlefield", "PutGraveyardCardIntoHand",
        "ReturnDeadGraveyardCardToTopOfLibrary", "PutPermanentOnTopOfOwnersLibrary") { node, args, tvar ->
        val a = node.strField("_Action")
        // ReturnDead… ("return this card from the graveyard") often has no ref -> Self
        var tgt = refTarget(args, tvar)
        if (tgt == null) {
            if (a == "ReturnDeadGraveyardCardToTopOfLibrary") tgt = "EffectTarget.Self" else return@on null
        }
        val zone = mapOf(
            "PutGraveyardCardOntoBattlefield" to "BATTLEFIELD", "PutGraveyardCardIntoHand" to "HAND",
            "ReturnDeadGraveyardCardToTopOfLibrary" to "LIBRARY", "PutPermanentOnTopOfOwnersLibrary" to "LIBRARY",
        )[a]
        if (a == "PutPermanentOnTopOfOwnersLibrary" || a == "ReturnDeadGraveyardCardToTopOfLibrary") {
            call("Effects.Move", arg(Lit(tgt)), arg("Zone.$zone"), arg("ZonePlacement.Top"))
        } else {
            call("Effects.Move", arg(Lit(tgt)), arg("Zone.$zone"))
        }
    }
}

internal fun EmitCtx.renderSearch(args: JsonElement?): Dsl? {
    val blob = compact(args)
    // A destination CHOICE ("put it into your hand or graveyard") or a destination we don't model
    // (graveyard) can't be rendered as a single fixed SearchDestination — scaffold rather than silently
    // pick one arm (Dina's Guidance).
    if ("ChooseAnAction" in blob || "PutFoundCardsIntoGraveyard" in blob) return null
    val dest = when {
        "PutFoundCardsOntoBattlefield" in blob -> "BATTLEFIELD"
        "PutFoundCardsIntoHand" in blob -> "HAND"
        "PutSetAsideCardsOnTopOfLibrary" in blob || "OnTopOfLibrary" in blob -> "TOP_OF_LIBRARY"
        else -> "HAND"
    }
    // "search for a card named X" (Avarax, Daru Cavalier, …) -> a name filter, which the land/type
    // search filter can't express.
    val named = Regex(""""NamedCard",\s*"args":\s*"([^"]+)"""").find(blob)?.groupValues?.get(1)
    val searchSubtype = Regex(""""IsCreatureType",\s*"args":\s*"(\w+)"""").find(blob)?.groupValues?.get(1)
    val filt = when {
        named != null -> "GameObjectFilter.Any.named(\"$named\")"
        searchSubtype != null -> "GameObjectFilter.Any.withSubtype(\"$searchSubtype\")"  // "an Elf card"
        else -> landSearchFilterDsl(args)
    }
    val count = findInteger(args)
    val parts = mutableListOf(arg("filter", filt))
    if (count is Int && count != 1) parts.add(arg("count", "$count"))
    parts.add(arg("destination", "SearchDestination.$dest"))
    if ("EntersTapped" in blob) parts.add(arg("entersTapped", "true"))  // "...onto the battlefield tapped"
    if ("RevealFoundCards" in blob) parts.add(arg("reveal", "true"))
    return Call("Patterns.Library.searchLibrary", parts)
}

internal fun EmitCtx.renderLook(node: JsonObject, args: JsonElement?, tvar: String?): Dsl? {
    val look = findInteger(node) ?: return null
    val blob = compact(node)
    // A conditional look ("...if there is an instant and a sorcery card in your graveyard, instead put
    // two of them...") branches the kept count; the flat lookAtTopAndKeep can't express it (and the
    // keep-count regex below would wrongly read the conditional branch's number), so scaffold.
    if ("IfElse" in blob || "_Condition" in blob) return null
    if (oracleText?.contains("target", ignoreCase = true) == true) {
        if (node.strField("_Action") != "LookAtTheTopNumberCardsOfPlayersLibrary" || tvar == null) return null
        if ("PutAGenericCardIntoGraveyard" !in blob || "PutTheRemainingCardsOnTopOfLibraryInAnyOrder" !in blob) return null
        // The look-and-distribute pipeline keeps its hand-indented multi-line element strings as Raw —
        // a shape no leaf node models yet — inside the multi-line Composite node.
        return Composite(listOf(
            Lit("GatherCardsEffect(CardSource.TopOfLibrary(DynamicAmount.Fixed($look), Player.TargetOpponent), storeAs = \"looked\")"),
            Raw(
                "SelectFromCollectionEffect(\n" +
                    "                from = \"looked\",\n" +
                    "                selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(1)),\n" +
                    "                storeSelected = \"toGraveyard\",\n" +
                    "                storeRemainder = \"toTop\",\n" +
                    "                selectedLabel = \"Put in graveyard\",\n" +
                    "                remainderLabel = \"Put on top\"\n" +
                    "            )",
            ),
            Lit("MoveCollectionEffect(from = \"toGraveyard\", destination = CardDestination.ToZone(Zone.GRAVEYARD, Player.TargetOpponent))"),
            Raw(
                "MoveCollectionEffect(\n" +
                    "                from = \"toTop\",\n" +
                    "                destination = CardDestination.ToZone(Zone.LIBRARY, Player.TargetOpponent, ZonePlacement.Top),\n" +
                    "                order = CardOrder.ControllerChooses\n" +
                    "            )",
            ),
        ))
    }
    var keep: Int? = null
    for (m in Regex(""""PutNumber\w*IntoHand".*?"args":\s*(\d+)""").findAll(blob)) keep = m.groupValues[1].toInt()
    if (keep != null) return call("Patterns.Library.lookAtTopAndKeep", arg("count", "$look"), arg("keepCount", "$keep"))
    if ("PutTheRemainingCardsOnTopOfLibraryInAnyOrder" in blob) return call("Patterns.Library.lookAtTopAndReorder", arg("count", "$look"))
    return null
}
