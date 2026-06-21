package com.wingedsheep.engine.core

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit coverage for the tap/untap atoms ([tap] / [untapOrConsumeStun]) — the single chokepoint
 * every battlefield tap and untap routes through (enforced corpus-wide by
 * [com.wingedsheep.engine.hygiene.TapEventEnforcementTest]).
 *
 * The atoms exist so the state change and its [TappedEvent] / [UntappedEvent] can never drift
 * apart — the bug that silently dropped station and declare-attackers taps. These tests pin that
 * contract: every real transition emits exactly one event, and every non-transition emits none
 * (CR 603.2f / 603.6e), including the stun-counter replacement (CR 122.1d).
 */
class TapAtomTest : ScenarioTestBase() {

    init {
        test("tap() taps an untapped permanent and emits exactly one TappedEvent") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Spined Wurm", tapped = false)
                .build()
            val wurm = game.findPermanent("Spined Wurm")!!

            val (newState, event) = tap(game.state, wurm)

            newState.getEntity(wurm)?.has<TappedComponent>() shouldBe true
            event.shouldBeInstanceOf<TappedEvent>()
            event!!.entityId shouldBe wurm
        }

        test("tap() is a no-op with no event on an already-tapped permanent (CR 603.2f)") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Spined Wurm", tapped = true)
                .build()
            val wurm = game.findPermanent("Spined Wurm")!!

            val (newState, event) = tap(game.state, wurm)

            event shouldBe null
            newState shouldBe game.state
        }

        test("untapOrConsumeStun() untaps a tapped permanent and emits exactly one UntappedEvent") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Spined Wurm", tapped = true)
                .build()
            val wurm = game.findPermanent("Spined Wurm")!!

            val (newState, events) = untapOrConsumeStun(game.state, wurm)

            newState.getEntity(wurm)?.has<TappedComponent>() shouldBe false
            events.size shouldBe 1
            events.single().shouldBeInstanceOf<UntappedEvent>()
        }

        test("untapOrConsumeStun() consumes a stun counter instead of untapping, emitting no event (CR 122.1d)") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Spined Wurm", tapped = true)
                .build()
            val wurm = game.findPermanent("Spined Wurm")!!
            game.state = game.state.updateEntity(wurm) {
                it.with(CountersComponent(mapOf(CounterType.STUN to 2)))
            }

            val (newState, events) = untapOrConsumeStun(game.state, wurm)

            // Stays tapped; one stun counter removed; no UntappedEvent (it never became untapped).
            newState.getEntity(wurm)?.has<TappedComponent>() shouldBe true
            newState.getEntity(wurm)?.get<CountersComponent>()?.getCount(CounterType.STUN) shouldBe 1
            events.shouldBeEmpty()
        }

        test("untapOrConsumeStun() is a no-op with no event on an already-untapped permanent") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Spined Wurm", tapped = false)
                .build()
            val wurm = game.findPermanent("Spined Wurm")!!

            val (newState, events) = untapOrConsumeStun(game.state, wurm)

            events.shouldBeEmpty()
            newState shouldBe game.state
        }
    }
}
