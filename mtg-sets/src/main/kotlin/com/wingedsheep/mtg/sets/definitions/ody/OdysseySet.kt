package com.wingedsheep.mtg.sets.definitions.ody

import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Odyssey (2001)
 *
 * Set Code: ODY
 * Release Date: October 1, 2001
 */
object OdysseySet : MtgSet {

    override val code = "ODY"
    override val displayName = "Odyssey"
    override val releaseDate = "2001-10-01"
    override val block = "Odyssey"
    override val basicLandsFallback = PortalSet
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.ody.cards"
}
