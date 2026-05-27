package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Stores the color chosen when this permanent entered the battlefield.
 * Used by cards like Riptide Replicator ("As this artifact enters, choose a color").
 */
@Serializable
data class ChosenColorComponent(
    val color: Color
) : Component

/**
 * Stores the creature type chosen when this permanent entered the battlefield.
 * Used by cards like Doom Cannon ("As this artifact enters, choose a creature type").
 */
@Serializable
data class ChosenCreatureTypeComponent(
    val creatureType: String
) : Component

/**
 * Stores the creature chosen when this permanent entered the battlefield.
 * Used by cards like Dauntless Bodyguard ("As this creature enters, choose another creature you control").
 */
@Serializable
data class ChosenCreatureComponent(
    val creatureId: EntityId
) : Component

/**
 * Stores the basic land type chosen when this permanent entered the battlefield.
 * Used by auras like Phantasmal Terrain ("As this Aura enters, choose a basic land type").
 * Read by [com.wingedsheep.sdk.scripting.SetEnchantedLandTypeFromChosen] to set the
 * enchanted land's type.
 */
@Serializable
data class ChosenLandTypeComponent(
    val landType: String
) : Component

/**
 * Stores the named mode chosen when this permanent entered the battlefield.
 * Used by cards whose entry choice is a card-defined list of options (e.g., the
 * Khans cycle of Sieges: "As this enters, choose Khans or Dragons"). The stored
 * [modeId] is the stable id from the `EntersWithChoice(ChoiceType.MODE,...)`
 * declaration; conditions read it via
 * [com.wingedsheep.sdk.scripting.conditions.SourceChosenModeIs].
 */
@Serializable
data class ChosenModeComponent(
    val modeId: String
) : Component
