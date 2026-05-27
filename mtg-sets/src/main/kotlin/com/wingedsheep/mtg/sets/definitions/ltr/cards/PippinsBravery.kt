package com.wingedsheep.mtg.sets.definitions.ltr.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.OptionalCostEffect
import com.wingedsheep.sdk.scripting.effects.SacrificeEffect

/**
 * Pippin's Bravery
 * {G}
 * Instant
 *
 * You may sacrifice a Food. If you do, target creature gets +4/+4 until end of turn.
 * Otherwise, that creature gets +2/+2 until end of turn.
 */
val PippinsBravery = card("Pippin's Bravery") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "You may sacrifice a Food. If you do, target creature gets +4/+4 until end of turn. Otherwise, that creature gets +2/+2 until end of turn."

    spell {
        val creature = target("target creature", Targets.Creature)
        effect = OptionalCostEffect(
            cost = SacrificeEffect(filter = GameObjectFilter.Any.withSubtype("Food")),
            ifPaid = Effects.ModifyStats(4, 4, creature),
            ifNotPaid = Effects.ModifyStats(2, 2, creature)
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "182"
        artist = "John Tedrick"
        flavorText = "Pippin stabbed upwards, and the written blade of Westernesse pierced through the hide and went deep into the vitals of the troll."
        imageUri = "https://cards.scryfall.io/normal/front/b/c/bc60dc65-6813-4d57-877b-df195ed00d00.jpg?1686969536"
    }
}
