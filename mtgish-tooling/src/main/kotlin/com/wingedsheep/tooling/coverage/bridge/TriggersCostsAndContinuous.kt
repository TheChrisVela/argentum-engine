package com.wingedsheep.tooling.coverage.bridge

/** Triggered-ability conditions and costs (accepted as [supported] pending a `Triggers.*`/`Costs.*`
 *  facade scan), plus the duration-scoped trigger/replacement creators (composed from primitives). */
internal fun BridgeBuilder.triggersCostsAndContinuous() {
    // Triggers — validated by a Triggers.* facade scan in a later phase.
    supported("WhenAPermanentEntersTheBattlefield", "trigger: ETB (Triggers.* scan validates in P1)")
    supported("WhenACreatureOrPlaneswalkerDies", "trigger: dies")
    supported("WhenACreatureAttacks", "trigger: attacks")
    supported("WhenACreatureBlocks", "trigger: blocks (Ydwen Efreet)")
    supported("WhenACreatureDealsCombatDamageToAPlayer", "trigger: combat damage to player")
    supported("WhenAPlayerCastsASpell", "trigger: a player casts a spell (Triggers.YouCastSpell / AnyPlayerCastsSpell / OpponentCastsSpell + type filters)")
    supported("AtTheBeginningOfAPlayersUpkeep", "trigger: upkeep (Triggers.YourUpkeep / EachUpkeep / EachOpponentUpkeep)")
    supported("AtTheBeginningOfAPlayersEndStep", "trigger: end step (Triggers.YourEndStep / EachEndStep)")
    supported("WhenAPermanentBecomesTheTargetOfASpellOrAbility", "trigger: becomes target (Triggers.BecomesTargetByOpponent / BecomesTarget / CreatureYouControlBecomesTargetByOpponent)")

    // Intervening-if conditions (CR 603.4) gating a TriggerI, plus the Mount "while saddled" gate. The
    // emitter renders the recognised shapes to `triggerCondition = Conditions.*`; an unrenderable
    // condition still scaffolds, so these are accepted as supported vocabulary (the emitter is the gate).
    supported("PermanentPassesFilter", "condition: a permanent matches a filter (e.g. ThisPermanent IsSaddled -> Conditions.SourceIsSaddled)")
    supported("PlayerPassesFilter", "condition: a player matches a filter (e.g. You HasntCastASpellThisTurn)")
    supported("IsSaddled", "predicate: this permanent is saddled (CR 702.171b)")
    supported("HasntCastASpellThisTurn", "predicate: player hasn't cast a (filtered) spell this turn")
    supported("WasCastFromTheirHand", "predicate: spell cast from the player's hand (fromZone = HAND)")
    // Source-relative Mount/Vehicle payoff filter: "a creature that crewed/saddled it this turn"
    // (Giant Beaver) -> GameObjectFilter.Creature.crewedOrSaddledSourceThisTurn().
    supported("SaddledPermanentThisTurn", "filter: a creature that saddled this permanent this turn (crewedOrSaddledSourceThisTurn)")
    supported("CrewedPermanentThisTurn", "filter: a creature that crewed this permanent this turn (crewedOrSaddledSourceThisTurn)")

    // Costs.
    supported("PayMana", "cost: pay mana (universal)")
    supported("SacrificeAPermanent", "cost: sacrifice")
    supported("SacrificeNumberPermanents", "cost: sacrifice N")
    // "Pay N life" as an activation cost -> Costs.PayLife(n). The emitter renders fixed-integer amounts
    // (abilityCostDsl); non-integer amounts ({X}, life-total halves, …) are declined -> SCAFFOLD.
    supported("PayLife", "cost: pay life")
    composed("DiscardACardOfType", "cost: discard filtered")

    // Duration-scoped continuous trigger / replacement creators.
    composed("CreateReplaceWouldDealDamageUntil", "PreventDamageShield / RedirectNextDamage", composes = listOf("PreventDamageShield"))
    composed("CreateTriggerUntil", "CreateGlobalTriggeredAbility (duration)", composes = listOf("CreateGlobalTriggeredAbility"))
    composed("CreateFutureTrigger", "CreateDelayedTrigger", composes = listOf("CreateDelayedTrigger"))
}
