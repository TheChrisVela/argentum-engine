package com.wingedsheep.mtg.sets.definitions.spm.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Hide on the Ceiling
 * {X}{U}
 * Instant
 * Exile X target artifacts and/or creatures. Return them to the battlefield
 * under their owners' control at the beginning of the next end step.
 */
val HideOnTheCeiling = card("Hide on the Ceiling") {
    manaCost = "{X}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile X target artifacts and/or creatures. Return them to the battlefield under their owners' control at the beginning of the next end step."

    spell {
        val target = target("target artifact or creature", Targets.CreatureOrArtifact)
        effect = EffectPatterns.exileUntilEndStep(target)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "65"
        artist = "Jarel Threat"
        imageUri = "https://cards.scryfall.io/normal/front/7/a/7a18d1c3-e9cc-472e-861e-4b0eec2be8c4.jpg?1757377827"
    }
}
