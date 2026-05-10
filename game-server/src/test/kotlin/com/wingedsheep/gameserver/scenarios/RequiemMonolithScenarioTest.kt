package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Requiem Monolith.
 *
 * Card reference:
 * - Requiem Monolith ({2}{B}): Artifact
 *   "{T}: Until end of turn, target creature gains 'Whenever this creature is dealt damage,
 *    you draw that many cards and lose that much life.' That creature's controller may have
 *    this artifact deal 1 damage to it. Activate only as a sorcery."
 *
 * Exercises the new `MayEffect.decisionMaker` field — the may decision is delegated to the
 * targeted creature's controller rather than the activator.
 */
class RequiemMonolithScenarioTest : ScenarioTestBase() {

    init {
        context("Requiem Monolith granted trigger + delegated may") {

            test("targeting own creature, accept may: draw 1, lose 1 life") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Requiem Monolith")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialHand = game.handSize(1)
                val initialLife = game.getLifeTotal(1)

                val monolithId = game.findPermanent("Requiem Monolith")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val ability = cardRegistry.getCard("Requiem Monolith")!!.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = monolithId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                withClue("Activation should succeed: ${result.error}") { result.error shouldBe null }

                game.resolveStack()

                withClue("May decision should be pending") { game.hasPendingDecision() shouldBe true }
                withClue("Player1 controls Glory Seeker, so player1 decides") {
                    game.state.pendingDecision?.playerId shouldBe game.player1Id
                }

                game.answerYesNo(true)
                game.resolveStack()

                withClue("Player1 draws 1 from granted trigger") { game.handSize(1) shouldBe initialHand + 1 }
                withClue("Player1 loses 1 life from granted trigger") { game.getLifeTotal(1) shouldBe initialLife - 1 }
                withClue("Glory Seeker survives 1 damage") { game.isOnBattlefield("Glory Seeker") shouldBe true }
            }

            test("targeting own creature, decline may: no damage, no draw, no life loss") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Requiem Monolith")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialHand = game.handSize(1)
                val initialLife = game.getLifeTotal(1)

                val monolithId = game.findPermanent("Requiem Monolith")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val ability = cardRegistry.getCard("Requiem Monolith")!!.script.activatedAbilities.first()

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = monolithId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                game.resolveStack()
                game.answerYesNo(false)
                game.resolveStack()

                game.handSize(1) shouldBe initialHand
                game.getLifeTotal(1) shouldBe initialLife
                game.isOnBattlefield("Glory Seeker") shouldBe true
            }

            test("targeting opponent's creature: opponent makes the may decision") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Requiem Monolith")
                    .withCardOnBattlefield(2, "Glory Seeker") // opponent controls
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val p2InitialHand = game.handSize(2)
                val p2InitialLife = game.getLifeTotal(2)

                val monolithId = game.findPermanent("Requiem Monolith")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val ability = cardRegistry.getCard("Requiem Monolith")!!.script.activatedAbilities.first()

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = monolithId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                game.resolveStack()

                withClue("Opponent (target's controller) is asked the may, not the activator") {
                    game.state.pendingDecision?.playerId shouldBe game.player2Id
                }

                // Opponent accepts to demonstrate the granted trigger reaches them.
                game.answerYesNo(true)
                game.resolveStack()

                withClue("Opponent draws 1 from the granted trigger they now control") {
                    game.handSize(2) shouldBe p2InitialHand + 1
                }
                withClue("Opponent loses 1 life") { game.getLifeTotal(2) shouldBe p2InitialLife - 1 }
            }
        }
    }
}
