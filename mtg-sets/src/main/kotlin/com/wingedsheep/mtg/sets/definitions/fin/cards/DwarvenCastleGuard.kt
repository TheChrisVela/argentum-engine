package com.wingedsheep.mtg.sets.definitions.fin.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity


/**
 * Dwarven Castle Guard
 * {1}{W}
 * Creature — Dwarf Soldier
 * 2/1
 * When this creature dies, create a 1/1 colorless Hero creature token.
 */
val DwarvenCastleGuard = card("Dwarven Castle Guard") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Dwarf Soldier"
    oracleText = "When this creature dies, create a 1/1 colorless Hero creature token."
    power = 2
    toughness = 1
    triggeredAbility {
        trigger = Triggers.Dies
        effect = Effects.CreateToken(power = 1, toughness = 1, creatureTypes = setOf("Hero"))
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "18"
        artist = "Crystal Fae"
        flavorText = "\"'Lali-ho' is the dwarven greeting!\""
        imageUri = "https://cards.scryfall.io/normal/front/e/1/e17c0d27-e88d-4ba9-acbb-3f916cee3d7e.jpg"
    }
}
