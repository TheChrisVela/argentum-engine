package com.wingedsheep.mtg.sets.definitions.spm.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Kapow!
 * {2}{G}
 * Instant
 * Put a +1/+1 counter on target creature you control. Then it fights target creature
 * an opponent controls.
 */
val Kapow = card("Kapow!") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Put a +1/+1 counter on target creature you control. Then it fights target creature an opponent controls."

    spell {
        val yourCreature = target("creature you control", Targets.CreatureYouControl)
        val theirCreature = target("creature an opponent controls", Targets.CreatureOpponentControls)
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, yourCreature)
            .then(Effects.Fight(yourCreature, theirCreature))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "167"
        artist = "Juliann Tiu"
        imageUri = "https://cards.scryfall.io/normal/front/9/5/954d8ab3-2378-47cb-b5b1-6bc5b5ec4e37.jpg?1757380058"
    }
}
