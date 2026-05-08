package com.wingedsheep.sdk.model

/**
 * A Magic: The Gathering set: a named collection of card definitions that can be
 * registered into the engine and (optionally) used for sealed/draft.
 *
 * Implementations are expected to self-stamp the [setCode] on every card returned
 * by [cards] and [basicLands], so consumers do not have to remember to call
 * `.copy(setCode = ...)`.
 */
interface MtgSet {

    /** Three-letter set code (e.g. "EOE"). Stable identifier across the codebase. */
    val code: String

    /** Display name (e.g. "Edge of Eternities"). */
    val displayName: String

    /** All non-basic-land card definitions in the set, with [code] already stamped. */
    val cards: List<CardDefinition>

    /** Basic land definitions, with [code] already stamped. Empty if the set has none. */
    val basicLands: List<CardDefinition> get() = emptyList()

    /** Block (e.g. "Onslaught") if the set is part of one, otherwise null. */
    val block: String? get() = null

    /** Total card count in the official set (used by booster generation when set is incomplete). */
    val totalSetSize: Int? get() = null

    /** True when this set's [cards] list is incomplete relative to the official set. */
    val incomplete: Boolean get() = false

    /** Booster packs for this set guarantee a legendary creature. */
    val guaranteedLegendary: Boolean get() = false

    /**
     * If this set has no basic lands of its own, the set whose lands should be
     * registered alongside it (Scourge and Legions reuse Onslaught lands).
     */
    val basicLandsFallback: MtgSet? get() = null

    /** Whether the set is wired into the booster generator for sealed/draft. */
    val sealedSupported: Boolean get() = false
}
