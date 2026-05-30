package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Explosive Growth.
 *
 * Explosive Growth ({G}, Instant, Kicker {5}): "Target creature gets +2/+2 until end
 * of turn. If this spell was kicked, that creature gets +5/+5 until end of turn instead."
 */
class ExplosiveGrowthScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Explosive Growth") {

            test("unkicked gives target creature +2/+2") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Explosive Growth")
                    .withLandsOnBattlefield(1, "Forest", 1) // {G}
                    .withCardOnBattlefield(1, "Hill Giant") // 3/3
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!

                val castResult = game.castSpell(1, "Explosive Growth", giant)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Hill Giant should be 5/5 after +2/+2") {
                    projected.getPower(giant) shouldBe 5
                    projected.getToughness(giant) shouldBe 5
                }
            }

            test("kicked gives target creature +5/+5 instead") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Explosive Growth")
                    .withLandsOnBattlefield(1, "Forest", 6) // {G} + {5} kicker
                    .withCardOnBattlefield(1, "Hill Giant") // 3/3
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!
                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Explosive Growth"
                }

                val castResult = game.execute(
                    CastSpell(
                        playerId,
                        cardId,
                        targets = listOf(ChosenTarget.Permanent(giant)),
                        wasKicked = true
                    )
                )
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Hill Giant should be 8/8 after +5/+5 (kicked)") {
                    projected.getPower(giant) shouldBe 8
                    projected.getToughness(giant) shouldBe 8
                }
            }
        }
    }
}
