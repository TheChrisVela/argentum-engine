package com.wingedsheep.gameserver.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gameserver.deck.DeckValidationResult
import com.wingedsheep.gameserver.deck.DeckValidator
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST endpoints supporting the deck picker UI and the standalone deckbuilder:
 *
 * - `GET  /api/decks/cards`     — metadata for every card in the registry (picker stats + deckbuilder grid/search).
 * - `GET  /api/decks/examples`  — built-in example decks the picker offers as starting points.
 * - `POST /api/decks/validate`  — server-authoritative deck validation (count rules, unknown cards, ≥60).
 *
 * The cards endpoint covers both the lightweight picker (which only reads name/cost/types/colors/cmc
 * for stats and validation) and the standalone deckbuilder (which additionally needs oracle text,
 * power/toughness, keywords, and an image URI for the card grid). Optional fields default to null/empty
 * so existing picker callers ignore them transparently.
 */
@RestController
@RequestMapping("/api/decks")
class DecksController(
    private val cardRegistry: CardRegistry,
    private val deckValidator: DeckValidator,
) {

    data class CardSummaryDTO(
        val name: String,
        val manaCost: String,
        val cmc: Int,
        val colors: List<String>,
        // Scryfall-style color identity (CR 903.4): mana-cost colors plus oracle-text colored
        // symbols plus basic-land-subtype colors, with the per-card override applied. Drives the
        // deckbuilder's color filter — for deck construction "this card is red" means "its
        // identity is red", not just "its printed cost has red". `colors` stays available for
        // mana-curve and pip stats that genuinely care about printed cost.
        val colorIdentity: List<String>,
        val cardTypes: List<String>,
        val supertypes: List<String>,
        val subtypes: List<String>,
        val basicLand: Boolean,
        val rarity: String,
        val setCode: String?,
        val collectorNumber: String?,
        val oracleText: String? = null,
        val power: String? = null,
        val toughness: String? = null,
        val imageUri: String? = null,
        val keywords: List<String> = emptyList(),
        val legalFormats: List<String> = emptyList(),
        val isDoubleFaced: Boolean = false,
        val backFaceName: String? = null,
        val backFaceImageUri: String? = null,
    )

    data class ExampleDeckDTO(
        val id: String,
        val name: String,
        val description: String,
        val cards: Map<String, Int>
    )

    data class ValidateRequest(
        val deckList: Map<String, Int>,
        val format: DeckFormat? = null,
        /**
         * Optional designated commander for Commander/Brawl/Standard Brawl decks. When present
         * (and the format is commander-shaped) the validator runs the full commander rule set:
         * eligibility, color identity, and structural shape with the commander in the command
         * zone instead of the library.
         */
        val commander: String? = null,
    )

    @GetMapping("/cards")
    fun getCards(): List<CardSummaryDTO> =
        cardRegistry.allCardNames()
            .asSequence()
            .filter { it !in TOKEN_NAMES }
            .mapNotNull { cardRegistry.getCard(it) }
            .map { it.toSummary() }
            .sortedBy { it.name }
            .toList()

    @GetMapping("/examples")
    fun getExamples(): List<ExampleDeckDTO> = EXAMPLE_DECKS

    @PostMapping("/validate")
    fun validate(@RequestBody request: ValidateRequest): DeckValidationResult {
        // Route through the Deck overload only when the caller has supplied a commander —
        // an absent field means the (legacy) Map-based validation path, which doesn't enforce
        // commander rules. The deckList already carries flat card counts; the Deck overload
        // re-explodes the map into Deck.cards, which gives the validator the same total
        // counts a Map call would (the commander entry is added on top, matching the
        // singleton-cap and totalCards expectations).
        if (request.commander != null) {
            val cards = request.deckList.flatMap { (name, count) -> List(count) { name } }
            return deckValidator.validate(Deck(cards = cards, commander = request.commander), request.format)
        }
        return deckValidator.validate(request.deckList, request.format)
    }

    /**
     * Returns, for each submitted deck list, the set of formats it is fully legal in. This is
     * the authoritative source for the deckbuilder's saved-deck legality badges and the lobby
     * picker's "legal in this format" filter — both of which used to compute legality on the
     * client. We run [DeckValidator.validate] against every [DeckFormat] and include only the
     * formats that come back valid (per-card legality + format-specific construction rules).
     *
     * Batched on purpose: the saved-decks browser renders 50+ deck cards at once, and per-deck
     * round-trips would dominate the open-overlay latency. Pass an empty map to get an empty
     * map back.
     */
    @PostMapping("/legal-formats")
    fun legalFormats(@RequestBody request: LegalFormatsRequest): Map<String, List<String>> {
        if (request.decks.isEmpty()) return emptyMap()
        return request.decks.mapValues { (_, deckList) ->
            DeckFormat.entries
                .asSequence()
                .filter { format -> deckValidator.validate(deckList, format).valid }
                .map { it.name }
                .toList()
        }
    }

    data class LegalFormatsRequest(
        val decks: Map<String, Map<String, Int>> = emptyMap()
    )

    @GetMapping("/formats")
    fun getFormats(): List<FormatInfo> =
        DeckFormat.entries.map { FormatInfo(it.name, it.displayName) }

    data class FormatInfo(val id: String, val name: String)

    @GetMapping("/sets")
    fun getSets(): List<SetInfoDTO> =
        MtgSetCatalog.all.map { SetInfoDTO(it.code, it.displayName) }

    data class SetInfoDTO(val code: String, val name: String)

    private fun CardDefinition.toSummary(): CardSummaryDTO = CardSummaryDTO(
        name = name,
        manaCost = manaCost.toString(),
        cmc = cmc,
        colors = colors.map { it.name },
        colorIdentity = colorIdentity.map { it.name },
        cardTypes = typeLine.cardTypes.map { it.name },
        supertypes = typeLine.supertypes.map { it.name },
        subtypes = typeLine.subtypes.map { it.toString() },
        basicLand = typeLine.isBasicLand,
        rarity = metadata.rarity.name,
        setCode = setCode,
        collectorNumber = metadata.collectorNumber,
        oracleText = oracleText.takeIf { it.isNotBlank() },
        power = creatureStats?.power?.toString(),
        toughness = creatureStats?.toughness?.toString(),
        imageUri = metadata.imageUri,
        keywords = keywords.map { it.name }.sorted(),
        legalFormats = legalFormats.map { it.name }.sorted(),
        isDoubleFaced = isDoubleFaced,
        backFaceName = backFace?.name,
        backFaceImageUri = backFace?.metadata?.imageUri,
    )

    companion object {
        // Predefined tokens are registered in the CardRegistry so the engine can resolve
        // token abilities by name (e.g., a created Treasure → its mana ability), but they
        // are not real cards and must not appear in the deckbuilder catalog.
        private val TOKEN_NAMES: Set<String> =
            PredefinedTokens.allTokens.flatMap { listOfNotNull(it.name, it.backFace?.name) }.toSet()

        // Bloomburrow-only tribal decks. Selesnya Rabbits, Rakdos Lizards, Golgari Squirrels,
        // and Simic Frogs are taken from the Bloomburrow Constructed Midweek Magic decklists
        // (https://mtgazone.com/midweek-magic-bloomburrow-constructed/). Boros Mice and Orzhov
        // Bats are hand-built tribal lists restricted to Bloomburrow cards in the registry.
        private val EXAMPLE_DECKS = listOf(
            ExampleDeckDTO(
                id = "boros_mice",
                name = "Boros Mice",
                description = "RW Mice aggro from Bloomburrow.",
                cards = mapOf(
                    "Heartfire Hero" to 4,
                    "Flowerfoot Swordmaster" to 4,
                    "Emberheart Challenger" to 4,
                    "Manifold Mouse" to 4,
                    "Whiskervale Forerunner" to 4,
                    "Seedglaive Mentor" to 4,
                    "Might of the Meek" to 4,
                    "Crumb and Get It" to 4,
                    "Rabid Gnaw" to 4,
                    "Lupinflower Village" to 4,
                    "Rockface Village" to 4,
                    "Mountain" to 8,
                    "Plains" to 8
                )
            ),
            ExampleDeckDTO(
                id = "selesnya_rabbits",
                name = "Selesnya Rabbits",
                description = "GW Rabbits from Bloomburrow.",
                cards = mapOf(
                    "Pawpatch Recruit" to 4,
                    "Warren Elder" to 4,
                    "Burrowguard Mentor" to 4,
                    "Valley Questcaller" to 4,
                    "Finneas, Ace Archer" to 4,
                    "Harvestrite Host" to 4,
                    "Warren Warleader" to 4,
                    "Hop to It" to 4,
                    "Carrot Cake" to 4,
                    "Fabled Passage" to 4,
                    "Forest" to 7,
                    "Plains" to 13
                )
            ),
            ExampleDeckDTO(
                id = "rakdos_lizards",
                name = "Rakdos Lizards",
                description = "BR Lizards from Bloomburrow.",
                cards = mapOf(
                    "Iridescent Vinelasher" to 4,
                    "Hired Claw" to 4,
                    "Fireglass Mentor" to 4,
                    "Flamecache Gecko" to 4,
                    "Gev, Scaled Scorch" to 4,
                    "Thought-Stalker Warlock" to 4,
                    "Valley Flamecaller" to 4,
                    "Take Out the Trash" to 4,
                    "Fell" to 4,
                    "Fabled Passage" to 4,
                    "Rockface Village" to 2,
                    "Mountain" to 8,
                    "Swamp" to 10
                )
            ),
            ExampleDeckDTO(
                id = "golgari_squirrels",
                name = "Golgari Squirrels",
                description = "BG Squirrels from Bloomburrow.",
                cards = mapOf(
                    "Bonecache Overseer" to 4,
                    "Vinereap Mentor" to 4,
                    "Bakersbane Duo" to 2,
                    "Thornvault Forager" to 4,
                    "Osteomancer Adept" to 4,
                    "Valley Rotcaller" to 4,
                    "Bushy Bodyguard" to 2,
                    "Curious Forager" to 4,
                    "Camellia, the Seedmiser" to 4,
                    "Fell" to 4,
                    "Fabled Passage" to 4,
                    "Forest" to 10,
                    "Swamp" to 10
                )
            ),
            ExampleDeckDTO(
                id = "simic_frogs",
                name = "Simic Frogs",
                description = "GU Frogs from Bloomburrow.",
                cards = mapOf(
                    "Sunshower Druid" to 4,
                    "Valley Mightcaller" to 4,
                    "Pond Prophet" to 4,
                    "Three Tree Scribe" to 4,
                    "Dour Port-Mage" to 3,
                    "Long River Lurker" to 4,
                    "Clement, the Worrywort" to 3,
                    "Splash Lasher" to 2,
                    "Polliwallop" to 4,
                    "Splash Portal" to 4,
                    "Fabled Passage" to 4,
                    "Forest" to 11,
                    "Island" to 9
                )
            ),
            ExampleDeckDTO(
                id = "orzhov_bats",
                name = "Orzhov Bats",
                description = "WB Bats from Bloomburrow.",
                cards = mapOf(
                    "Essence Channeler" to 4,
                    "Starscape Cleric" to 4,
                    "Lifecreed Duo" to 4,
                    "Moonstone Harbinger" to 4,
                    "Starlit Soothsayer" to 4,
                    "Moonrise Cleric" to 4,
                    "Wax-Wane Witness" to 4,
                    "Zoraline, Cosmos Caller" to 3,
                    "Lunar Convocation" to 4,
                    "Sonar Strike" to 4,
                    "Fabled Passage" to 4,
                    "Uncharted Haven" to 3,
                    "Plains" to 7,
                    "Swamp" to 7
                )
            )
        )
    }
}
