package com.wingedsheep.mtg.sets.definitions.ltr.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/**
 * Meneldor, Swift Savior
 * {3}{U}
 * Legendary Creature — Bird Soldier
 * 3/3
 *
 * Flying
 * Whenever Meneldor deals combat damage to a player, exile up to one target creature you own,
 * then return it to the battlefield under your control.
 */
val MeneldorSwiftSavior = card("Meneldor, Swift Savior") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Creature — Bird Soldier"
    power = 3
    toughness = 3
    oracleText = "Flying\n" +
        "Whenever Meneldor deals combat damage to a player, exile up to one target creature you own, " +
        "then return it to the battlefield under your control."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.DealsCombatDamageToPlayer
        val creature = target(
            "up to one target creature you own",
            TargetCreature(
                optional = true,
                filter = TargetFilter(GameObjectFilter.Creature.ownedByYou())
            )
        )
        effect = CompositeEffect(listOf(
            MoveToZoneEffect(creature, Zone.EXILE),
            MoveToZoneEffect(creature, Zone.BATTLEFIELD)
        ))
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "62"
        artist = "Axel Sauerwald"
        flavorText = "\"Then come! We have need of speed greater than any wind, outmatching the wings of the Nazgûl.\"\n—Gandalf"
        imageUri = "https://cards.scryfall.io/normal/front/6/2/62d2ee20-abbc-4a9d-8d30-4223242123e8.jpg?1686968213"
    }
}
