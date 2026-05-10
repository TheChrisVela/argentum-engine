package com.wingedsheep.mtg.sets.definitions.ktk

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/**
 * Per-printing rows for cards that are reprinted in Khans of Tarkir but whose canonical
 * [com.wingedsheep.sdk.model.CardDefinition] lives elsewhere. Pure presentation data — no
 * script, no behavior. Engine identity is the card name.
 */
internal val KTKReprints: List<Printing> = listOf(
    Printing(
        oracleId = "bdb3ca68-ec1f-4e16-81cc-d23f8f52c728",
        name = "Naturalize",
        setCode = "KTK",
        collectorNumber = "142",
        scryfallId = "b129b44e-a1ce-41f2-a0cf-b6c879c7cbbd",
        artist = "James Paick",
        imageUri = "https://cards.scryfall.io/normal/front/b/1/b129b44e-a1ce-41f2-a0cf-b6c879c7cbbd.jpg?1562792044",
        releaseDate = "2014-09-26",
        rarity = Rarity.COMMON,
    ),
)
