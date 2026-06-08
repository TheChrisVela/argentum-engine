package com.wingedsheep.tooling.coverage.bridge

/** Evergreen keywords that map straight to a `Keyword` enum member. Most other keywords resolve for
 *  free via the probe's PascalCase→enum auto-resolve; these are the ones worth pinning explicitly. */
internal fun BridgeBuilder.keywords() {
    keyword("Flying", "FLYING")
    keyword("Haste", "HASTE")
    keyword("Vigilance", "VIGILANCE")
    keyword("Reach", "REACH")
    keyword("Defender", "DEFENDER")
    // Saddle N (CR 702.171) — a PARAMETERIZED keyword ability (the N count rides in the rule's args),
    // NOT a bare card keyword. It must be `supported`, not `keyword`: a `keyword` entry would make
    // `keywordLines` stamp a bare `keywords(Keyword.SADDLE)` on the card and drop the N, exactly the
    // parameterized-keyword trap the `keywordLines` guard warns about (and why Crew carries no bridge
    // entry at all). The emitter's explicit `rname == "Saddle"` branch renders
    // `keywordAbility(KeywordAbility.saddle(N))`; this `supported` entry only marks the capability as
    // covered (never blocking) so the probe doesn't report Saddle as a gap.
    supported("Saddle", "keyword ability: Saddle N -> keywordAbility(KeywordAbility.saddle(N)) (CR 702.171)")

    composed("Landwalk", "specific *WALK keywords (SWAMPWALK, FORESTWALK, ...)")
    // Equip is a keyword ability, but the engine has no `Keyword.EQUIP` enum member: `equipAbility(cost)`
    // synthesises the sorcery-speed "attach to target creature you control" activated ability whose
    // resolution is `AttachEquipment`. So it's composed, not a bare keyword (which would emit a
    // non-existent enum). The emitter renders only the canonical unrestricted shape (see Emitter.equipAbilityLine).
    composed("Equip", "equip: equipAbility(cost) -> sorcery-speed AttachEquipment activated ability", composes = listOf("AttachEquipment"))
    // Cycling (CR 702.29) is a keyword ability with no `Keyword.CYCLING` enum member — `KeywordAbility.cycling(cost)`
    // synthesises the activated "Discard this card: Draw a card" ability. Like Equip it's composed, not a bare
    // keyword (PascalCase auto-resolve would look for a non-existent CYCLING enum and block it). The emitter renders
    // the canonical pure-mana shape (see Emitter.kt `rname == "Cycling"`).
    composed("Cycling", "cycling: KeywordAbility.cycling(cost) -> 'Discard this card: Draw a card' activated ability", composes = listOf("DrawCards"))
}
