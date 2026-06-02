package com.wingedsheep.mtg.sets.definitions.tmt.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Negate
 * {1}{U}
 * Instant
 *
 * Counter target noncreature spell.
 */
val Negate = card("Negate") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target noncreature spell."

    spell {
        target("noncreature spell", Targets.NoncreatureSpell)
        effect = Effects.CounterSpell()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "47"
        artist = "Ryan Valle"
        flavorText = "\"You guys seriously try to do this with jump kicks?\""
        imageUri = "https://cards.scryfall.io/normal/front/5/2/52d58fe4-6070-4022-9cd7-c35a11b44525.jpg?1771342360"
    }
}
