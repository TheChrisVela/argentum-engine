package com.wingedsheep.gameserver.config

import com.wingedsheep.ai.engine.deck.RandomDeckGenerator
import com.wingedsheep.ai.engine.SealedDeckGenerator
import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gameserver.sealed.SetConfigs
import com.wingedsheep.mtg.sets.definitions.bloomburrow.BloomburrowSet
import com.wingedsheep.mtg.sets.definitions.brotherswar.TheBrothersWarSet
import com.wingedsheep.mtg.sets.definitions.dft.AetherdriftSet
import com.wingedsheep.mtg.sets.definitions.edgeofeternities.EdgeOfEternitiesSet
import com.wingedsheep.mtg.sets.definitions.dominaria.DominariaSet
import com.wingedsheep.mtg.sets.definitions.dominariaunited.DominariaUnitedSet
import com.wingedsheep.mtg.sets.definitions.khans.KhansOfTarkirSet
import com.wingedsheep.mtg.sets.definitions.legions.LegionsSet
import com.wingedsheep.mtg.sets.definitions.onslaught.OnslaughtSet
import com.wingedsheep.mtg.sets.definitions.one.PhyrexiaAllWillBeOneSet
import com.wingedsheep.mtg.sets.definitions.portal.PortalSet
import com.wingedsheep.mtg.sets.definitions.lorwyneclipsed.LorwynEclipsedSet
import com.wingedsheep.mtg.sets.definitions.lostcavernsofixalan.LostCavernsOfIxalanSet
import com.wingedsheep.mtg.sets.definitions.mkm.MurdersAtKarlovManorSet
import com.wingedsheep.mtg.sets.definitions.duskmourn.DuskmournSet
import com.wingedsheep.mtg.sets.definitions.innistradmidnighthunt.InnistradMidnightHuntSet
import com.wingedsheep.mtg.sets.definitions.spiderman.SpiderManSet
import com.wingedsheep.mtg.sets.definitions.wildsofeldraineset.WildsOfEldrainSet
import com.wingedsheep.mtg.sets.definitions.foundations.FoundationsSet
import com.wingedsheep.mtg.sets.definitions.scourge.ScourgeSet
import com.wingedsheep.mtg.sets.definitions.custom.JustOneGlassToken
import com.wingedsheep.mtg.sets.definitions.custom.SekshaasEarlySleeper
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.model.CardDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private fun List<CardDefinition>.stamp(setCode: String): List<CardDefinition> =
    map { if (it.setCode == null) it.copy(setCode = setCode) else it }

@Configuration
class GameBeansConfig(
    private val gameProperties: GameProperties
) {

    @Bean
    fun cardRegistry(): CardRegistry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        register(PortalSet.cards.stamp(PortalSet.code))
        register(PortalSet.basicLands)
        if (gameProperties.sets.onslaughtEnabled) {
            register(OnslaughtSet.cards.stamp(OnslaughtSet.code))
            register(OnslaughtSet.basicLands)
        }
        if (gameProperties.sets.scourgeEnabled) {
            register(ScourgeSet.cards.stamp(ScourgeSet.code))
        }
        if (gameProperties.sets.legionsEnabled) {
            register(LegionsSet.cards.stamp(LegionsSet.code))
        }
        // Scourge/Legions use Onslaught basic lands — register them even if Onslaught itself isn't enabled
        if (!gameProperties.sets.onslaughtEnabled &&
            (gameProperties.sets.scourgeEnabled || gameProperties.sets.legionsEnabled)) {
            register(OnslaughtSet.basicLands)
        }
        if (gameProperties.sets.khansEnabled) {
            register(KhansOfTarkirSet.cards.stamp(KhansOfTarkirSet.code))
            register(KhansOfTarkirSet.basicLands)
        }
        if (gameProperties.sets.phyrexiaAllWillBeOneEnabled) {
            register(PhyrexiaAllWillBeOneSet.cards.stamp(PhyrexiaAllWillBeOneSet.code))
        }
        if (gameProperties.sets.dominariaEnabled) {
            register(DominariaSet.cards.stamp(DominariaSet.code))
            register(DominariaSet.basicLands)
        }
        if (gameProperties.sets.dominariaUnitedEnabled) {
            register(DominariaUnitedSet.cards.stamp(DominariaUnitedSet.code))
        }
        if (gameProperties.sets.bloomburrowEnabled) {
            register(BloomburrowSet.cards.stamp(BloomburrowSet.code))
            register(BloomburrowSet.basicLands)
        }
        if (gameProperties.sets.brothersWarEnabled) {
            register(TheBrothersWarSet.cards.stamp(TheBrothersWarSet.code))
        }
        if (gameProperties.sets.aetherdriftEnabled) {
            register(AetherdriftSet.cards.stamp(AetherdriftSet.code))
        }
        if (gameProperties.sets.edgeOfEternitiesEnabled) {
            register(EdgeOfEternitiesSet.cards.stamp(EdgeOfEternitiesSet.code))
            register(EdgeOfEternitiesSet.basicLands)
        }
        if (gameProperties.sets.lorwynEclipsedEnabled) {
            register(LorwynEclipsedSet.cards.stamp(LorwynEclipsedSet.code))
            register(LorwynEclipsedSet.basicLands)
        }
        if (gameProperties.sets.lostCavernsOfIxalanEnabled) {
            register(LostCavernsOfIxalanSet.cards.stamp(LostCavernsOfIxalanSet.code))
        }
        if (gameProperties.sets.murdersAtKarlovManorEnabled) {
            register(MurdersAtKarlovManorSet.cards.stamp(MurdersAtKarlovManorSet.code))
        }
        if (gameProperties.sets.foundationsEnabled) {
            register(FoundationsSet.cards.stamp(FoundationsSet.code))
        }
        if (gameProperties.sets.duskmournEnabled) {
            register(DuskmournSet.cards.stamp(DuskmournSet.code))
        }
        if (gameProperties.sets.innistradMidnightHuntEnabled) {
            register(InnistradMidnightHuntSet.cards.stamp(InnistradMidnightHuntSet.code))
        }
        if (gameProperties.sets.spiderManEnabled) {
            register(SpiderManSet.cards.stamp(SpiderManSet.code))
        }
        if (gameProperties.sets.wildsOfEldrainEnabled) {
            register(WildsOfEldrainSet.cards.stamp(WildsOfEldrainSet.code))
        }
        // Easter egg card — injected into Rick's deck at game start
        register(SekshaasEarlySleeper)
        register(JustOneGlassToken)
    }

    @Bean
    fun boosterGenerator(): BoosterGenerator {
        val sets = buildMap {
            put(PortalSet.code, SetConfigs.portalSetConfig)
            if (gameProperties.sets.onslaughtEnabled) {
                put(OnslaughtSet.code, SetConfigs.onslaughtSetConfig)
            }
            if (gameProperties.sets.scourgeEnabled) {
                put(ScourgeSet.code, SetConfigs.scourgeSetConfig)
            }
            if (gameProperties.sets.legionsEnabled) {
                put(LegionsSet.code, SetConfigs.legionsSetConfig)
            }
            if (gameProperties.sets.khansEnabled) {
                put(KhansOfTarkirSet.code, SetConfigs.khansSetConfig)
            }
            if (gameProperties.sets.dominariaEnabled) {
                put(DominariaSet.code, SetConfigs.dominariaSetConfig)
            }
            if (gameProperties.sets.bloomburrowEnabled) {
                put(BloomburrowSet.code, SetConfigs.bloomburrowSetConfig)
            }
            if (gameProperties.sets.lorwynEclipsedEnabled) {
                put(LorwynEclipsedSet.code, SetConfigs.lorwynEclipsedSetConfig)
            }
            if (gameProperties.sets.edgeOfEternitiesEnabled) {
                put(EdgeOfEternitiesSet.code, SetConfigs.edgeOfEternitiesSetConfig)
            }
        }
        return BoosterGenerator(sets)
    }

    @Bean
    fun sealedDeckGenerator(boosterGenerator: BoosterGenerator): SealedDeckGenerator =
        SealedDeckGenerator(boosterGenerator)

    @Bean
    fun randomDeckGenerator(): RandomDeckGenerator = RandomDeckGenerator(
        cardPool = buildList {
            if (gameProperties.sets.onslaughtEnabled) addAll(OnslaughtSet.cards)
            if (gameProperties.sets.scourgeEnabled) addAll(ScourgeSet.cards)
            if (gameProperties.sets.legionsEnabled) addAll(LegionsSet.cards)
            if (gameProperties.sets.khansEnabled) addAll(KhansOfTarkirSet.cards)
            if (gameProperties.sets.phyrexiaAllWillBeOneEnabled) addAll(PhyrexiaAllWillBeOneSet.cards)
            if (gameProperties.sets.dominariaEnabled) addAll(DominariaSet.cards)
            if (gameProperties.sets.dominariaUnitedEnabled) addAll(DominariaUnitedSet.cards)
            if (gameProperties.sets.bloomburrowEnabled) addAll(BloomburrowSet.cards)
            if (gameProperties.sets.brothersWarEnabled) addAll(TheBrothersWarSet.cards)
            if (gameProperties.sets.edgeOfEternitiesEnabled) addAll(EdgeOfEternitiesSet.cards)
            if (gameProperties.sets.lorwynEclipsedEnabled) addAll(LorwynEclipsedSet.cards)
            if (gameProperties.sets.murdersAtKarlovManorEnabled) addAll(MurdersAtKarlovManorSet.cards)
        },
        basicLandVariants = PortalSet.basicLands,
        setCodes = buildList {
            if (gameProperties.sets.onslaughtEnabled) add("ONS")
            if (gameProperties.sets.scourgeEnabled) add("SCG")
            if (gameProperties.sets.legionsEnabled) add("LGN")
            if (gameProperties.sets.khansEnabled) add("KTK")
            if (gameProperties.sets.phyrexiaAllWillBeOneEnabled) add("ONE")
            if (gameProperties.sets.dominariaEnabled) add("DOM")
            if (gameProperties.sets.dominariaUnitedEnabled) add("DMU")
            if (gameProperties.sets.bloomburrowEnabled) add("BLB")
            if (gameProperties.sets.brothersWarEnabled) add("BRO")
            if (gameProperties.sets.edgeOfEternitiesEnabled) add("EOE")
            if (gameProperties.sets.murdersAtKarlovManorEnabled) add("MKM")
        }
    )
}
