package com.wingedsheep.mtg.sets.definitions.tmt.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Make Your Move
 * {2}{W}
 * Instant
 *
 * Destroy target artifact, enchantment, or creature with power 4 or
 * greater.
 */
val MakeYourMove = card("Make Your Move") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Destroy target artifact, enchantment, or creature with power 4 or greater."

    spell {
        val permanent = target(
            "artifact, enchantment, or creature with power 4 or greater",
            TargetPermanent(
                filter = TargetFilter(
                    GameObjectFilter(
                        cardPredicates = listOf(
                            CardPredicate.Or(
                                listOf(
                                    CardPredicate.IsArtifact,
                                    CardPredicate.IsEnchantment,
                                    CardPredicate.PowerAtLeast(4),
                                )
                            )
                        )
                    )
                )
            )
        )
        effect = Effects.Destroy(permanent)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "20"
        artist = "Nathaniel Himawan"
        flavorText = "For a moment, there is only relentless snow and frozen breath."
        imageUri = "https://cards.scryfall.io/normal/front/e/d/ed8bdd98-6377-40cf-b381-cee38b1bda2a.jpg?1771502526"
    }
}
