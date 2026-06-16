package com.wingedsheep.mtg.sets.definitions.atq.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.PayOrSufferEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Yawgmoth Demon
 * {4}{B}{B}
 * Creature — Phyrexian Demon
 * 6/6
 * Flying, first strike
 * At the beginning of your upkeep, you may sacrifice an artifact. If you don't, tap this
 * creature and it deals 2 damage to you.
 *
 * Modeled with the curated punisher primitive [PayOrSufferEffect] ("do [suffer] unless you
 * [cost]"): the avoidable cost is sacrificing an artifact, and the `suffer` is the upkeep tax —
 * tap this creature and deal 2 damage to its controller. When the controller has an artifact they
 * choose whether to sacrifice one (decline → suffer); with no artifact the cost is unpayable and
 * the tax applies automatically. This is the "you may sacrifice …; if you don't, …" reading.
 */
val YawgmothDemon = card("Yawgmoth Demon") {
    manaCost = "{4}{B}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Phyrexian Demon"
    power = 6
    toughness = 6
    oracleText = "Flying, first strike\n" +
        "At the beginning of your upkeep, you may sacrifice an artifact. If you don't, tap " +
        "this creature and it deals 2 damage to you."

    keywords(Keyword.FLYING, Keyword.FIRST_STRIKE)

    triggeredAbility {
        trigger = Triggers.YourUpkeep
        effect = PayOrSufferEffect(
            cost = Costs.pay.Sacrifice(GameObjectFilter.Artifact),
            suffer = Effects.Composite(
                listOf(
                    Effects.Tap(EffectTarget.Self),
                    Effects.DealDamage(2, EffectTarget.Controller, damageSource = EffectTarget.Self)
                )
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "21"
        artist = "Sandra Everingham"
        imageUri = "https://cards.scryfall.io/normal/front/0/4/04bbd231-0d5f-4cbf-92a7-10d2c5c4b82c.jpg?1562895987"
    }
}
