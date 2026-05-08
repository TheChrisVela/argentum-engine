package com.wingedsheep.mtg.sets.definitions.fdn.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Fact or Fiction {3}{U}
 * Instant
 *
 * Reveal the top five cards of your library. An opponent separates those cards
 * into two piles. Put one pile into your hand and the other into your graveyard.
 *
 * Originally printed in Invasion (2000); reused here as the canonical "divvy"
 * mechanic exemplar (CR 700.3 piles).
 */
val FactOrFiction = card("Fact or Fiction") {
    manaCost = "{3}{U}"
    typeLine = "Instant"
    oracleText = "Reveal the top five cards of your library. An opponent separates those cards into two piles. Put one pile into your hand and the other into your graveyard."

    spell {
        effect = EffectPatterns.factOrFiction(count = 5)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        artist = "Ron Spencer"
        flavorText = "What you believe to be true and what is actually true are rarely the same."
        imageUri = "https://cards.scryfall.io/normal/front/9/c/9c0f7e15-3b3a-4f9b-9e63-32ada6e1e9a5.jpg"
    }
}
