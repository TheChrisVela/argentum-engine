package com.wingedsheep.gameserver.sealed

import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.mtg.sets.definitions.bloomburrow.BloomburrowSet
import com.wingedsheep.mtg.sets.definitions.dominaria.DominariaSet
import com.wingedsheep.mtg.sets.definitions.edgeofeternities.EdgeOfEternitiesSet
import com.wingedsheep.mtg.sets.definitions.khans.KhansOfTarkirSet
import com.wingedsheep.mtg.sets.definitions.legions.LegionsSet
import com.wingedsheep.mtg.sets.definitions.lorwyneclipsed.LorwynEclipsedSet
import com.wingedsheep.mtg.sets.definitions.onslaught.OnslaughtSet
import com.wingedsheep.mtg.sets.definitions.portal.PortalSet
import com.wingedsheep.mtg.sets.definitions.scourge.ScourgeSet

/**
 * Application-level registry of set configurations used by the booster generator.
 *
 * This file lives in `game-server` rather than `rules-engine` because it
 * imports concrete card-set modules from `mtg-sets`, and `rules-engine` must
 * stay free of card-specific dependencies (see CLAUDE.md). The booster
 * generator itself is pure logic and lives in `rules-engine`; this object
 * is the consumer-side catalogue that wires specific sets into it.
 */
object SetConfigs {
    val portalSetConfig = BoosterGenerator.SetConfig(
        setCode = PortalSet.code,
        setName = PortalSet.displayName,
        cards = PortalSet.cards,
        basicLands = PortalSet.basicLands
    )

    val onslaughtSetConfig = BoosterGenerator.SetConfig(
        setCode = OnslaughtSet.code,
        setName = OnslaughtSet.displayName,
        cards = OnslaughtSet.cards,
        basicLands = OnslaughtSet.basicLands,
        block = "Onslaught"
    )

    val scourgeSetConfig = BoosterGenerator.SetConfig(
        setCode = ScourgeSet.code,
        setName = ScourgeSet.displayName,
        cards = ScourgeSet.cards,
        basicLands = OnslaughtSet.basicLands, // Scourge has no basic lands; use Onslaught block lands
        block = "Onslaught"
    )

    val legionsSetConfig = BoosterGenerator.SetConfig(
        setCode = LegionsSet.code,
        setName = LegionsSet.displayName,
        cards = LegionsSet.cards,
        basicLands = OnslaughtSet.basicLands, // Legions has no basic lands; use Onslaught block lands
        incomplete = false,
        block = "Onslaught",
        totalSetSize = 145
    )

    val khansSetConfig = BoosterGenerator.SetConfig(
        setCode = KhansOfTarkirSet.code,
        setName = KhansOfTarkirSet.displayName,
        cards = KhansOfTarkirSet.cards,
        basicLands = KhansOfTarkirSet.basicLands,
        incomplete = false,
        totalSetSize = 249
    )

    val dominariaSetConfig = BoosterGenerator.SetConfig(
        setCode = DominariaSet.code,
        setName = DominariaSet.displayName,
        cards = DominariaSet.cards,
        basicLands = DominariaSet.basicLands,
        incomplete = true,
        guaranteedLegendary = true
    )

    val bloomburrowSetConfig = BoosterGenerator.SetConfig(
        setCode = BloomburrowSet.code,
        setName = BloomburrowSet.displayName,
        cards = BloomburrowSet.cards,
        basicLands = BloomburrowSet.basicLands,
        incomplete = false,
        totalSetSize = 272
    )

    val lorwynEclipsedSetConfig = BoosterGenerator.SetConfig(
        setCode = LorwynEclipsedSet.code,
        setName = LorwynEclipsedSet.displayName,
        cards = LorwynEclipsedSet.cards,
        basicLands = LorwynEclipsedSet.basicLands,
        incomplete = false,
        totalSetSize = 273
    )

    val edgeOfEternitiesSetConfig = BoosterGenerator.SetConfig(
        setCode = EdgeOfEternitiesSet.code,
        setName = EdgeOfEternitiesSet.displayName,
        cards = EdgeOfEternitiesSet.cards,
        basicLands = EdgeOfEternitiesSet.basicLands,
        incomplete = true
    )
}
