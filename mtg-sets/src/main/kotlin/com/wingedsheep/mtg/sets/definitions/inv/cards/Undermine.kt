package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Undermine
 * {U}{U}{B}
 * Instant
 * Counter target spell. Its controller loses 3 life.
 */
val Undermine = card("Undermine") {
    manaCost = "{U}{U}{B}"
    colorIdentity = "UB"
    typeLine = "Instant"
    oracleText = "Counter target spell. Its controller loses 3 life."

    spell {
        target = Targets.Spell
        effect = Effects.CounterSpell() then Effects.LoseLife(3, EffectTarget.TargetController)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "282"
        artist = "Massimiliano Frezzato"
        imageUri = "https://cards.scryfall.io/normal/front/2/3/2334bc71-5f85-47ff-b393-601a1e746a4e.jpg?1762258073"
    }
}
