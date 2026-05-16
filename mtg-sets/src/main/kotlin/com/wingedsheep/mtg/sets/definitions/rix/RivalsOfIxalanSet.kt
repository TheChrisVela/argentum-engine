package com.wingedsheep.mtg.sets.definitions.rix

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Rivals of Ixalan (2018)
 *
 * Set Code: RIX
 * Release Date: January 19, 2018
 */
object RivalsOfIxalanSet : MtgSet {

    override val code = "RIX"
    override val displayName = "Rivals of Ixalan"
    override val releaseDate = "2018-01-19"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.rix.cards"
}
