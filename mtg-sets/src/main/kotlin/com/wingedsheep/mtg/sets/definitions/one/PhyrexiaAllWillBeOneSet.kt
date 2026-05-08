package com.wingedsheep.mtg.sets.definitions.one

import com.wingedsheep.mtg.sets.definitions.one.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Phyrexia: All Will Be One (2023)
 *
 * Set Code: ONE
 * Release Date: February 10, 2023
 */
object PhyrexiaAllWillBeOneSet : MtgSet {

    override val code = "ONE"
    override val displayName = "Phyrexia: All Will Be One"

    override val cards: List<CardDefinition> = listOf(
        BlackcleaveCliffs,
        CopperlineGorge,
        DarkslickShores,
        RazorvergeThicket,
        SkrelvDefectorMite,
        SeachromeCoast,
    )
}
