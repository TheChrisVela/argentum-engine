package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Astelli Reclaimer's enters ability:
 * "return target noncreature, nonland permanent card with mana value X or less from your
 * graveyard to the battlefield, where X is the amount of mana spent to cast this creature."
 *
 * The interesting seam is that X depends on *how* the creature was cast, not its mana value:
 *  - cast for {3}{W}{W} → X = 5
 *  - cast with warp {2}{W} → X = 3
 *
 * These exercise the new
 * [com.wingedsheep.sdk.scripting.predicates.CardPredicate.ManaValueAtMostEntityManaSpent]
 * target predicate, which reads the source permanent's `CastRecordComponent` snapshot. The
 * contrast case (test 2) returns the *same* mana-value-4 artifact under a normal cast but
 * refuses it under a warp cast, proving X tracks mana actually spent.
 *
 * Icy Manipulator is a mana-value-4 artifact; All-Fates Scroll is a mana-value-3 artifact.
 */
class AstelliReclaimerScenarioTest : ScenarioTestBase() {

    init {
        test("cast for {3}{W}{W} (X=5): returns a mana value 4 permanent from graveyard") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Astelli Reclaimer")
                .withCardInGraveyard(1, "Icy Manipulator") // mana value 4 ≤ 5
                .withLandsOnBattlefield(1, "Plains", 5)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val castResult = game.castSpell(1, "Astelli Reclaimer")
            withClue("Casting Astelli Reclaimer for its full cost should succeed: ${castResult.error}") {
                castResult.error shouldBe null
            }
            // Resolve the creature spell — it enters, and the enters trigger pauses for a target.
            game.resolveStack()

            val icyInGraveyard = game.findCardsInGraveyard(1, "Icy Manipulator").firstOrNull()
                ?: error("Icy Manipulator should still be a legal graveyard target")
            game.selectTargets(listOf(icyInGraveyard))
            game.resolveStack()

            withClue("Icy Manipulator (MV 4 ≤ X=5) should be returned to the battlefield") {
                game.isOnBattlefield("Icy Manipulator") shouldBe true
            }
            withClue("Icy Manipulator should no longer be in the graveyard") {
                game.isInGraveyard(1, "Icy Manipulator") shouldBe false
            }
        }

        test("cast with warp {2}{W} (X=3): returns the MV-3 permanent but not the MV-4 one") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Astelli Reclaimer")
                .withCardInGraveyard(1, "All-Fates Scroll") // mana value 3 ≤ 3 — legal
                .withCardInGraveyard(1, "Icy Manipulator")  // mana value 4 > 3 — illegal
                .withLandsOnBattlefield(1, "Plains", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val astelliId = game.findCardsInHand(1, "Astelli Reclaimer").firstOrNull()
                ?: error("Astelli Reclaimer should be in hand")
            val castResult = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = astelliId,
                    useAlternativeCost = true // warp {2}{W} → 3 mana spent
                )
            )
            withClue("Warp cast should succeed: ${castResult.error}") {
                castResult.error shouldBe null
            }
            game.resolveStack()

            // Only All-Fates Scroll (MV 3 ≤ X=3) is a legal target; Icy Manipulator (MV 4) is not.
            val scrollInGraveyard = game.findCardsInGraveyard(1, "All-Fates Scroll").firstOrNull()
                ?: error("All-Fates Scroll should be a legal graveyard target")
            game.selectTargets(listOf(scrollInGraveyard))
            game.resolveStack()

            withClue("All-Fates Scroll (MV 3 ≤ X=3) should be returned to the battlefield") {
                game.isOnBattlefield("All-Fates Scroll") shouldBe true
            }
            withClue("Icy Manipulator (MV 4 > X=3) was never a legal target and stays in the graveyard") {
                game.isInGraveyard(1, "Icy Manipulator") shouldBe true
            }
            withClue("Icy Manipulator should not have entered the battlefield") {
                game.isOnBattlefield("Icy Manipulator") shouldBe false
            }
        }

        test("cast with warp {2}{W} (X=3): with only a MV-4 permanent, the trigger has no legal target") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Astelli Reclaimer")
                .withCardInGraveyard(1, "Icy Manipulator") // mana value 4 > 3 — no legal target
                .withLandsOnBattlefield(1, "Plains", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val astelliId = game.findCardsInHand(1, "Astelli Reclaimer").firstOrNull()
                ?: error("Astelli Reclaimer should be in hand")
            val castResult = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = astelliId,
                    useAlternativeCost = true
                )
            )
            withClue("Warp cast should succeed: ${castResult.error}") {
                castResult.error shouldBe null
            }
            game.resolveStack()

            withClue("Astelli Reclaimer should have entered the battlefield (warp cast)") {
                game.isOnBattlefield("Astelli Reclaimer") shouldBe true
            }
            withClue("The enters trigger had no legal target (MV 4 > X=3), so Icy Manipulator stays") {
                game.isInGraveyard(1, "Icy Manipulator") shouldBe true
            }
            withClue("No target decision should be pending — the trigger found no legal target") {
                game.hasPendingDecision() shouldBe false
            }
        }
    }
}
