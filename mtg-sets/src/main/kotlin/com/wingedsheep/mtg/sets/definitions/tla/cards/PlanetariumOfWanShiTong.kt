package com.wingedsheep.mtg.sets.definitions.tla.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CastFromCollectionWithoutPayingCostEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Planetarium of Wan Shi Tong
 * {6}
 * Legendary Artifact
 * {1}, {T}: Scry 2.
 * Whenever you scry or surveil, look at the top card of your library. You may cast that
 * card without paying its mana cost. Do this only once each turn. (Look at the card
 * after you scry or surveil.)
 *
 * The scry-or-surveil payoff is gated to fire at most once per turn (`oncePerTurn = true`),
 * matching "Do this only once each turn." Its effect is an atomic pipeline:
 *   1. [GatherCardsEffect] from the top of the library (count 1) — defaults to a private
 *      controller look ("look at the top card of your library").
 *   2. [MayEffect] wrapping [CastFromCollectionWithoutPayingCostEffect] — the optional
 *      "you may cast that card without paying its mana cost", which synthesizes the cast
 *      through the normal stack machinery so any target / X / mode prompts surface.
 */
val PlanetariumOfWanShiTong = card("Planetarium of Wan Shi Tong") {
    manaCost = "{6}"
    typeLine = "Legendary Artifact"
    oracleText = "{1}, {T}: Scry 2.\n" +
        "Whenever you scry or surveil, look at the top card of your library. You may cast " +
        "that card without paying its mana cost. Do this only once each turn. (Look at the " +
        "card after you scry or surveil.)"

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Effects.Scry(2)
    }

    triggeredAbility {
        trigger = Triggers.WheneverYouScryOrSurveil
        oncePerTurn = true
        effect = Effects.Composite(
            GatherCardsEffect(
                source = CardSource.TopOfLibrary(
                    count = DynamicAmount.Fixed(1),
                    player = Player.You,
                ),
                storeAs = "top",
            ),
            MayEffect(CastFromCollectionWithoutPayingCostEffect(from = "top")),
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "259"
        artist = "Robin Olausson"
        flavorText = "It charts the movements of heavenly bodies, forecasting the fates of nations."
        imageUri = "https://cards.scryfall.io/normal/front/0/e/0ebaf0bf-7aa2-469d-bdbb-0fbf6741eede.jpg?1764121913"
    }
}
