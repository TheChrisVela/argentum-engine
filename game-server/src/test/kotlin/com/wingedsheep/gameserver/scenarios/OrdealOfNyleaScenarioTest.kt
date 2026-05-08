package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Ordeal of Nylea.
 *
 * Card reference:
 * - Ordeal of Nylea ({1}{G}): Enchantment — Aura
 *   "Enchant creature"
 *   "Whenever enchanted creature attacks, put a +1/+1 counter on it. Then if it has three or more
 *    +1/+1 counters on it, sacrifice this Aura."
 *   "When you sacrifice this Aura, search your library for up to two basic land cards, put them
 *    onto the battlefield tapped, then shuffle."
 *
 * Regression coverage for two bugs found during initial implementation:
 * 1. AddCountersExecutor used the stateless `resolveTarget` overload, which returns null for
 *    `EffectTarget.EnchantedCreature` (needs `AttachedToComponent` lookup).
 * 2. The 3+ counter check used `EntityReference.AffectedEntity` (only populated during projection)
 *    instead of `EntityReference.Triggering` (set by AttachmentTriggerDetector to the attached
 *    creature for `EnchantedCreatureAttacks`).
 */
class OrdealOfNyleaScenarioTest : ScenarioTestBase() {

    init {
        context("Ordeal of Nylea") {
            test("first attack puts a +1/+1 counter on the enchanted creature") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Ordeal of Nylea")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val grizzlyBearsId = game.findPermanent("Grizzly Bears")!!
                val castResult = game.castSpell(1, "Ordeal of Nylea", grizzlyBearsId)
                withClue("Cast should succeed") { castResult.error shouldBe null }
                game.resolveStack()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val attackResult = game.declareAttackers(mapOf("Grizzly Bears" to 2))
                withClue("Attack declaration should succeed") { attackResult.error shouldBe null }

                // Resolve the attack-trigger ability.
                game.resolveStack()

                val counters = game.state.getEntity(grizzlyBearsId)?.get<CountersComponent>()
                withClue("Grizzly Bears should have one +1/+1 counter after attacking") {
                    counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }
                withClue("Ordeal of Nylea should still be on the battlefield (only 1 counter < 3)") {
                    game.isOnBattlefield("Ordeal of Nylea") shouldBe true
                }
            }

            test("attacking at 2 counters reaches 3, sacrifices the Aura, and tutors two basic lands tapped") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Ordeal of Nylea")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val grizzlyBearsId = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Ordeal of Nylea", grizzlyBearsId)
                game.resolveStack()

                // Pre-load Grizzly Bears with two +1/+1 counters so the next attack pushes it to
                // three and exercises the conditional sacrifice + sacrifice-trigger path.
                game.state = game.state.updateEntity(grizzlyBearsId) { container ->
                    val current = container.get<CountersComponent>() ?: CountersComponent()
                    container.with(current.withAdded(CounterType.PLUS_ONE_PLUS_ONE, 2))
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Grizzly Bears" to 2))
                game.resolveStack()

                withClue("Grizzly Bears should now have three +1/+1 counters") {
                    game.state.getEntity(grizzlyBearsId)
                        ?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
                }
                withClue("Ordeal of Nylea should have been sacrificed") {
                    game.isOnBattlefield("Ordeal of Nylea") shouldBe false
                    game.isInGraveyard(1, "Ordeal of Nylea") shouldBe true
                }

                val forestsBeforeSearch = game.findAllPermanents("Forest").toSet()

                // Resolve the sacrifice trigger by tutoring both Forests.
                val forestsInLibrary = game.state.getLibrary(game.player1Id).filter { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Forest"
                }
                withClue("Library should contain two Forests for the tutor decision") {
                    forestsInLibrary.size shouldBe 2
                }
                game.selectCards(forestsInLibrary)
                game.resolveStack()

                val newForests = game.findAllPermanents("Forest").filter { it !in forestsBeforeSearch }
                withClue("Two Forests should have been tutored onto the battlefield") {
                    newForests.size shouldBe 2
                }
                withClue("Both tutored Forests should be controlled by player 1") {
                    newForests.all { game.state.projectedState.getController(it) == game.player1Id } shouldBe true
                }
                withClue("Both tutored Forests should enter tapped") {
                    newForests.all { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe true
                }
            }
        }
    }
}
