package com.wingedsheep.mtg.sets.definitions.ltr.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Rise of the Witch-king
 * {2}{B}{G}
 * Sorcery
 *
 * Each player sacrifices a creature of their choice. If you sacrificed a creature this
 * way, you may return another permanent card from your graveyard to the battlefield.
 *
 * Composition:
 *  - `Effects.Sacrifice(Creature, count=1, target=Player.Each)` — `ForceSacrificeEffect`
 *    walks `state.turnOrder`, auto-sacrifices when a player has 0 or 1 creature, pauses
 *    for a choice when there are 2+. Each leg captures a `PermanentSnapshot` and threads
 *    it back into the underlying `EffectContinuation` so the sibling rider can read it.
 *  - The rider is a `ConditionalEffect` gated on `YouSacrificedThisWay` (LTR Gap 17),
 *    which checks `EffectContext.sacrificedPermanents` for any snapshot whose
 *    last-known controller was the source's controller.
 *  - The reanimation half is a target `Move` from graveyard → battlefield restricted to
 *    permanent cards owned by you. "Another" is handled implicitly: the Sacrifice
 *    effect has resolved by the time the target predicate evaluates, so the just-
 *    sacrificed permanent is in your graveyard but the predicate is on `Permanent`
 *    cards — any permanent-typed graveyard card matches, *including* the one just
 *    sacrificed (which is correct per CR; "another permanent card" lets you grab it back).
 */
val RiseOfTheWitchKing = card("Rise of the Witch-king") {
    manaCost = "{2}{B}{G}"
    colorIdentity = "BG"
    typeLine = "Sorcery"
    oracleText = "Each player sacrifices a creature of their choice. " +
        "If you sacrificed a creature this way, you may return another permanent card " +
        "from your graveyard to the battlefield."

    spell {
        val returnTarget = target(
            "another permanent card from your graveyard",
            TargetObject(
                filter = TargetFilter(GameObjectFilter.Permanent.ownedByYou(), zone = Zone.GRAVEYARD),
                optional = true
            )
        )
        effect = Effects.Sacrifice(
            GameObjectFilter.Creature,
            count = 1,
            target = EffectTarget.PlayerRef(Player.Each)
        ).then(
            ConditionalEffect(
                condition = Conditions.YouSacrificedThisWay,
                effect = Effects.Move(returnTarget, Zone.BATTLEFIELD, fromZone = Zone.GRAVEYARD)
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "221"
        artist = "Andrea Piparo"
        imageUri = "https://cards.scryfall.io/normal/front/3/f/3f440fa1-5387-41d6-a80f-5b19dbb21514.jpg?1686969962"
    }
}
