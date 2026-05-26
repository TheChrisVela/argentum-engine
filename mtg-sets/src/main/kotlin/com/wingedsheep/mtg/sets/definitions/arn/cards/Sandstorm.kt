package com.wingedsheep.mtg.sets.definitions.arn.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect
import com.wingedsheep.sdk.scripting.effects.ForEachInGroupEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Sandstorm
 * {G}
 * Instant
 * Sandstorm deals 1 damage to each attacking creature.
 */
val Sandstorm = card("Sandstorm") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Sandstorm deals 1 damage to each attacking creature."

    spell {
        effect = ForEachInGroupEffect(GroupFilter.AttackingCreatures, DealDamageEffect(1, EffectTarget.Self))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "53"
        artist = "Brian Snõddy"
        flavorText = "Even the landscape turned against Sarsour, first rising up and pelting him, then rearranging itself so he could no longer find his way."
        imageUri = "https://cards.scryfall.io/normal/front/7/3/73cba9cd-73d9-442e-bd99-9cba9f398b64.jpg?1562916488"
    }
}
