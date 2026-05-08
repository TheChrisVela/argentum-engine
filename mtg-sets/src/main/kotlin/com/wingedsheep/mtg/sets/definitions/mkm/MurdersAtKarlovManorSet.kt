package com.wingedsheep.mtg.sets.definitions.mkm

import com.wingedsheep.mtg.sets.definitions.mkm.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Murders at Karlov Manor (2024)
 *
 * Set Code: MKM
 * Release Date: February 9, 2024
 */
object MurdersAtKarlovManorSet : MtgSet {

    override val code = "MKM"
    override val displayName = "Murders at Karlov Manor"

    override val cards: List<CardDefinition> = listOf(
        CommercialDistrict,
        ElegantParlor,
        EscapeTunnel,
        HardHittingQuestion,
        HedgeMaze,
        LushPortico,
        MeticulousArchive,
        NoMoreLies,
        RaucousTheater,
        ShadowyBackstreet,
        ThunderingFalls,
        UndercitySewers,
        UndergroundMortuary,
    )
}
