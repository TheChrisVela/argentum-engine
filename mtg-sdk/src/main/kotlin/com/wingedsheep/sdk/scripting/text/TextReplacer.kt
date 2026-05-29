package com.wingedsheep.sdk.scripting.text

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Subtype

/**
 * Abstraction for performing text replacements on game data.
 *
 * Provided by the engine's TextReplacementComponent to the SDK types.
 * This allows SDK data classes to handle their own text replacement
 * without depending on engine internals.
 *
 * Covers the categories MTG text-changing effects touch. The subtype methods
 * also handle basic land types (those are subtypes too), so Crystal Spray's
 * "Forest" → "Island" rewrite flows through the same path as creature types.
 */
interface TextReplacer {
    /** Replace a creature type or basic land type string (e.g., "Elf" → "Goblin", "Forest" → "Island"). */
    fun replaceCreatureType(subtype: String): String

    /** Replace a Subtype value (creature type or basic land type). */
    fun replaceSubtype(subtype: Subtype): Subtype

    /** Replace a color word (e.g., RED → BLUE for "nonred" → "nonblue"). Returns the color unchanged if no rule applies. */
    fun replaceColor(color: Color): Color
}

/**
 * Marker interface for SDK types that can have their text replaced
 * by Layer 3 text-changing effects (e.g., Artificial Evolution).
 *
 * Every concrete implementation of a sealed interface that extends this
 * MUST implement [applyTextReplacement]. The Kotlin compiler enforces this,
 * guaranteeing that new types cannot silently skip text replacement.
 */
interface TextReplaceable<T> {
    /**
     * Returns a copy of this object with creature type text replaced,
     * or `this` if no replacements apply.
     */
    fun applyTextReplacement(replacer: TextReplacer): T
}
