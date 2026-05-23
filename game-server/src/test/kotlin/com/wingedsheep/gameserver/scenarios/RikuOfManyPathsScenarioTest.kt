package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Riku of Many Paths ({G}{U}{R} 3/3 Legendary Creature — Human Wizard).
 *
 * Oracle:
 *   "Whenever you cast a modal spell, choose up to X, where X is the number of times
 *    you chose a mode for that spell —
 *    • Exile the top card of your library. Until the end of your next turn, you may play it.
 *    • Put a +1/+1 counter on Riku. It gains trample until end of turn.
 *    • Create a 1/1 blue Bird creature token with flying."
 *
 * These tests double as the smoke tests for:
 *   - [com.wingedsheep.sdk.scripting.events.SpellCastPredicate.IsModal] — gates the
 *     trigger on the cast spell having at least one chosen mode.
 *   - `ContextPropertyKey.MODES_CHOSEN_ON_TRIGGERING_SPELL` + plumbing from
 *     `SpellCastEvent.chosenModesCount` → `TriggerContext.modesChosenCount` →
 *     `EffectContext.triggerModesChosenCount`.
 *   - [com.wingedsheep.sdk.scripting.effects.ModalEffect.dynamicChooseCount] —
 *     resolution-time "choose up to X" with X evaluated from the triggering spell.
 */
class RikuOfManyPathsScenarioTest : ScenarioTestBase() {

    private fun TestGame.pickOption(optionIndex: Int) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        submitDecision(OptionChosenResponse(decision.id, optionIndex))
    }

    init {
        context("Triggers only on modal spell casts") {

            test("non-modal spell cast does NOT trigger Riku") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Vault Plunderer")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Vault Plunderer's ETB trigger targets a player — passing priority resolves
                // the spell, then the trigger asks for its target before going on the stack.
                game.castSpell(1, "Vault Plunderer")
                game.resolveStack()
                game.selectTargets(listOf(game.player1Id))
                game.resolveStack()

                withClue("Riku must NOT present a mode choice for non-modal casts") {
                    game.getPendingDecision().shouldBeNull()
                }

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                withClue("Riku must still have zero +1/+1 counters") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
            }
        }

        context("Triggers on modal spell casts and routes by chosen mode") {

            test("modal cast → pick the +1/+1 + trample mode → Riku gains a counter and trample") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Dawn's Truce")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Dawn's Truce is choose-one — picking mode 0 (no-target hexproof branch)
                // makes the cast carry exactly one chosen mode. X for Riku's trigger = 1.
                game.castSpellWithMode(1, "Dawn's Truce", modeIndex = 0)
                game.resolveStack()

                // Riku's trigger resolves first (LIFO). With X = 1 it presents the
                // "choose up to one mode" decision (3 modes + decline).
                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("Riku must offer all three modes plus a decline option (chooseCount=1, min=0)") {
                    decision.options.size shouldBe 4
                }

                // Pick mode index 1 — "+1/+1 counter on Riku + trample until end of turn".
                game.pickOption(1)
                game.resolveStack()

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                withClue("Mode 2 must place a +1/+1 counter on Riku") {
                    counters.shouldNotBeNull()
                    counters.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }

                val projected = game.state.projectedState
                withClue("Mode 2 must grant Riku trample until end of turn") {
                    projected.getKeywords(rikuId) shouldBeContains Keyword.TRAMPLE.name
                }
            }

            test("modal cast → pick the Bird token mode → a 1/1 flying Bird joins the battlefield") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Dawn's Truce")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val birdsBefore = countBirdTokensControlledBy(game, game.player1Id)

                game.castSpellWithMode(1, "Dawn's Truce", modeIndex = 0)
                game.resolveStack()

                // Pick mode index 2 — "Create a 1/1 blue Bird creature token with flying".
                game.pickOption(2)
                game.resolveStack()

                val birdsAfter = countBirdTokensControlledBy(game, game.player1Id)
                withClue("Mode 3 must put one Bird token onto the battlefield") {
                    birdsAfter shouldBe birdsBefore + 1
                }
            }

            test("Brigid's Command cast via the cast-time picker triggers Riku only once total") {
                // Reproduces the real-game flow: empty chosenModes at cast, the cast-time
                // picker drives mode + per-mode-target prompts, and the spell only lands
                // on the stack once everything is selected. SpellCastEvent fires once →
                // Riku triggers once → presents exactly one "(1 of 2)" decision (X = 2).
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Brigid's Command")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val rikuId = game.findPermanent("Riku of Many Paths")!!

                // Cast Brigid's Command with no pre-chosen modes — engages the cast-time
                // mode picker just like the UI does.
                game.castSpell(1, "Brigid's Command")

                // First cast-time decision: pick Brigid's mode 1 ("Target player creates...").
                // Mode 0 (Kithkin copy) is filtered (Alice has no Kithkin to copy), so the
                // picker offers [mode 1, mode 2, mode 3] — mode 1 is at offered index 0.
                val brigidPick1 = game.getPendingDecision()
                brigidPick1.shouldNotBeNull()
                brigidPick1.shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("Brigid's first pick must be at cast time (1 of 2 for Brigid's Command)") {
                    brigidPick1.prompt shouldContain "Brigid's Command"
                    brigidPick1.prompt shouldContain "1 of 2"
                }
                game.pickOption(0)

                // Second cast-time decision: pick Brigid's mode 2 ("+3/+3 creature you control").
                // After removing mode 1, available = [mode 2, mode 3]; mode 2 is at offered index 0.
                val brigidPick2 = game.getPendingDecision()
                brigidPick2.shouldNotBeNull()
                brigidPick2.shouldBeInstanceOf<ChooseOptionDecision>()
                brigidPick2.prompt shouldContain "Brigid's Command"
                game.pickOption(0)

                // Per-mode targets: Brigid mode 1 wants a player; mode 2 wants a creature
                // you control. The cast-time picker prompts for each in pick order.
                game.selectTargets(listOf(game.player2Id))
                game.selectTargets(listOf(rikuId))

                // After the final target pick, the spell has landed on the stack and
                // SpellCastEvent has fired. Inspect the stack BEFORE resolving anything:
                // exactly one Riku trigger object should be there.
                val rikuTriggersOnStack = game.state.stack.count { stackId ->
                    val container = game.state.getEntity(stackId) ?: return@count false
                    val triggered = container.get<com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent>()
                    triggered?.sourceId == rikuId
                }
                withClue("Exactly one Riku trigger should be on the stack right after the cast finishes — not duplicated by detect+process running twice") {
                    rikuTriggersOnStack shouldBe 1
                }

                game.resolveStack()

                // Inspect what Riku presents.
                val rikuPick = game.getPendingDecision()
                rikuPick.shouldNotBeNull()
                rikuPick.shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("After full cast-time flow Riku must present ONE choose-up-to-X prompt — labelled 1 of 2 (X = 2)") {
                    rikuPick.prompt shouldContain "Riku of Many Paths"
                    rikuPick.prompt shouldContain "1 of 2"
                }

                // Resolve Riku's two mode picks: counter+trample, then Bird.
                game.pickOption(1)
                val rikuPick2 = game.getPendingDecision()
                rikuPick2.shouldNotBeNull()
                rikuPick2.shouldBeInstanceOf<ChooseOptionDecision>()
                rikuPick2.prompt shouldContain "2 of 2"
                game.pickOption(1)
                game.resolveStack()

                withClue("After Riku and Brigid resolve, no further decisions or stack entries") {
                    game.getPendingDecision().shouldBeNull()
                    game.state.stack.isEmpty() shouldBe true
                }

                // Sanity: each Riku mode applied exactly once.
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                countBirdTokensControlledBy(game, game.player1Id) shouldBe 1
            }

            test("choose-two modal cast → Riku triggers exactly once (not once per mode)") {
                // Regression: the trigger fires per SpellCastEvent, not per chosen mode.
                // A 2-mode Brigid's Command cast must produce exactly one AbilityTriggeredEvent
                // attributable to Riku — never one per mode.
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Brigid's Command")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val brigidsCommandId = game.state.getHand(game.player1Id).single { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Brigid's Command"
                }

                val perModeTargets = listOf(
                    listOf(ChosenTarget.Player(game.player2Id)),
                    listOf(ChosenTarget.Permanent(rikuId))
                )
                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = brigidsCommandId,
                        targets = perModeTargets.flatten(),
                        chosenModes = listOf(1, 2),
                        modeTargetsOrdered = perModeTargets
                    )
                )
                castResult.error shouldBe null

                val rikuTriggers = castResult.events.count { ev ->
                    ev is com.wingedsheep.engine.core.AbilityTriggeredEvent && ev.sourceId == rikuId
                }
                withClue("Riku's ability must fire exactly once per SpellCastEvent, not per mode") {
                    rikuTriggers shouldBe 1
                }
            }

            test("choose-two modal cast → Riku's X is exactly 2 (not more)") {
                // Brigid's Command is choose-two ({1}{G}{W}). Picking modes 1 and 2:
                //   mode 1 — "Target player creates a 1/1 G/W Kithkin token." → target Bob.
                //   mode 2 — "Target creature you control gets +3/+3 until end of turn." → target Riku.
                // chosenModes.size = 2, so X for Riku's trigger must be exactly 2 — two mode
                // prompts then resolution, never a third pick.
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Brigid's Command")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val brigidsCommandId = game.state.getHand(game.player1Id).single { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Brigid's Command"
                }

                // Cast Brigid's Command with TWO modes pre-chosen + per-mode targets.
                // `targets` must carry the flat union (see ModalCounteredTest).
                val perModeTargets = listOf(
                    listOf(ChosenTarget.Player(game.player2Id)),
                    listOf(ChosenTarget.Permanent(rikuId))
                )
                val castResult = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = brigidsCommandId,
                        targets = perModeTargets.flatten(),
                        chosenModes = listOf(1, 2),
                        modeTargetsOrdered = perModeTargets
                    )
                )
                withClue("Brigid's Command cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                // Riku's trigger goes on the stack after Brigid's Command, so it resolves
                // first (LIFO). It must present a "(1 of 2)" decision.
                val firstPrompt = game.getPendingDecision()
                firstPrompt.shouldNotBeNull()
                firstPrompt.shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("X must be 2 — first prompt should label the pick as 1 of 2") {
                    firstPrompt.prompt shouldContain "1 of 2"
                }

                // Pick mode 1 — +1/+1 counter on Riku + trample.
                game.pickOption(1)

                // Second pick — labelled (2 of 2). Mode 1 has been consumed (allowRepeat =
                // false per Scryfall ruling), so the remaining options are mode 0 (exile)
                // and mode 2 (Bird) plus a decline tail. Mode 2 sits at offered index 1.
                val secondPrompt = game.getPendingDecision()
                secondPrompt.shouldNotBeNull()
                secondPrompt.shouldBeInstanceOf<ChooseOptionDecision>()
                withClue("X = 2 must offer exactly two prompts; this one labelled 2 of 2") {
                    secondPrompt.prompt shouldContain "2 of 2"
                }
                withClue("Mode 1 must not be re-offered (Riku ruling: can't pick same mode twice per trigger)") {
                    secondPrompt.options.size shouldBe 3
                }

                // Bird-token mode is offered at index 1 (modes 0 and 2 of the original list).
                game.pickOption(1)
                game.resolveStack()

                // *** The critical assertion: no third mode prompt. X is exactly 2. ***
                val afterTwoPicks = game.getPendingDecision()
                withClue("After 2 picks, no further mode decision should be presented (X was 2, not 3)") {
                    afterTwoPicks.shouldBeNull()
                }

                // Both picked Riku modes resolved exactly once each: 1 counter, 1 Bird.
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                withClue("Riku gained exactly one +1/+1 counter from its mode 1 pick") {
                    counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }
                withClue("Mode 2 created exactly one Bird token under Alice's control") {
                    countBirdTokensControlledBy(game, game.player1Id) shouldBe 1
                }

                // Brigid's Command's two modes still resolved underneath Riku's trigger
                // — Bob has the Kithkin token; Riku still has trample (Riku's mode 1
                // granted trample until end of turn).
                val projected = game.state.projectedState
                withClue("Brigid's Command mode 1 must create a 1/1 G/W Kithkin token for Bob") {
                    countKithkinTokensControlledBy(game, game.player2Id) shouldBe 1
                }
                withClue("Riku's mode 1 still grants trample until end of turn") {
                    projected.getKeywords(rikuId) shouldBeContains Keyword.TRAMPLE.name
                }
            }

            test("modal cast → decline a mode → nothing happens but the trigger still fired") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardOnBattlefield(1, "Riku of Many Paths")
                    .withCardInHand(1, "Dawn's Truce")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellWithMode(1, "Dawn's Truce", modeIndex = 0)
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()

                // The decline option is the last entry per ModalEffectExecutor.
                val declineIndex = decision.options.lastIndex
                game.pickOption(declineIndex)
                game.resolveStack()

                val rikuId = game.findPermanent("Riku of Many Paths")!!
                val counters = game.state.getEntity(rikuId)?.get<CountersComponent>()
                withClue("Declining must leave Riku with no +1/+1 counters") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
                withClue("Declining must create no Bird tokens") {
                    countBirdTokensControlledBy(game, game.player1Id) shouldBe 0
                }
            }
        }
    }

    private infix fun <T> Iterable<T>.shouldBeContains(expected: T) {
        val found = this.any { it == expected }
        withClue("Expected to contain $expected but was ${this.toList()}") {
            found shouldBe true
        }
    }

    private fun countBirdTokensControlledBy(game: TestGame, playerId: com.wingedsheep.sdk.model.EntityId): Int =
        countTokensWithSubtypeControlledBy(game, playerId, "Bird")

    private fun countKithkinTokensControlledBy(game: TestGame, playerId: com.wingedsheep.sdk.model.EntityId): Int =
        countTokensWithSubtypeControlledBy(game, playerId, "Kithkin")

    private fun countTokensWithSubtypeControlledBy(
        game: TestGame,
        playerId: com.wingedsheep.sdk.model.EntityId,
        subtype: String
    ): Int {
        val battlefield = game.state.getZone(
            com.wingedsheep.engine.state.ZoneKey(playerId, Zone.BATTLEFIELD)
        )
        return battlefield.count { entityId ->
            val container = game.state.getEntity(entityId) ?: return@count false
            val card = container.get<CardComponent>() ?: return@count false
            container.get<TokenComponent>() != null &&
                subtype in card.typeLine.subtypes.map { it.value }
        }
    }
}
