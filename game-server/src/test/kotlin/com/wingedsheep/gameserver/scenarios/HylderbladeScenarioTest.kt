package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Hylderblade.
 *
 * Card reference:
 * - Hylderblade ({B}): Artifact — Equipment
 *   "Equipped creature gets +3/+1.
 *    Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn
 *    or a spell was warped this turn, attach this Equipment to target creature you control.
 *    Equip {4}"
 */
class HylderbladeScenarioTest : ScenarioTestBase() {

    init {
        context("Hylderblade equip + void-triggered re-attach") {

            test("equipping attaches Hylderblade to a creature you control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Hylderblade")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bladeId = game.findPermanent("Hylderblade")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val equipAbility = cardRegistry.getCard("Hylderblade")!!.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = bladeId,
                        abilityId = equipAbility.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                withClue("Equip activation should succeed: ${result.error}") { result.error shouldBe null }
                game.resolveStack()

                val attachedTo = game.state.getEntity(bladeId)!!.get<AttachedToComponent>()
                withClue("Hylderblade attached to Glory Seeker") {
                    attachedTo shouldNotBe null
                    attachedTo!!.targetId shouldBe seekerId
                }
            }

            test("equipped creature gets +3/+1") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Hylderblade")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 base
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bladeId = game.findPermanent("Hylderblade")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val equipAbility = cardRegistry.getCard("Hylderblade")!!.script.activatedAbilities.first()

                withClue("Glory Seeker base power") { game.state.projectedState.getPower(seekerId) shouldBe 2 }
                withClue("Glory Seeker base toughness") { game.state.projectedState.getToughness(seekerId) shouldBe 2 }

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = bladeId,
                        abilityId = equipAbility.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                game.resolveStack()

                withClue("Equipped creature power 2 + 3 = 5") {
                    game.state.projectedState.getPower(seekerId) shouldBe 5
                }
                withClue("Equipped creature toughness 2 + 1 = 3") {
                    game.state.projectedState.getToughness(seekerId) shouldBe 3
                }
            }

            test("Void NOT met: no end-step trigger") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Hylderblade")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardOnBattlefield(1, "Towering Baloth")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bladeId = game.findPermanent("Hylderblade")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val equipAbility = cardRegistry.getCard("Hylderblade")!!.script.activatedAbilities.first()

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = bladeId,
                        abilityId = equipAbility.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                game.resolveStack()

                game.passUntilPhase(Phase.ENDING, Step.END)

                withClue("No void trigger when nothing left the battlefield this turn") {
                    game.hasPendingDecision() shouldBe false
                }
                withClue("Hylderblade still attached to Glory Seeker (not re-attached)") {
                    game.state.getEntity(bladeId)!!.get<AttachedToComponent>()!!.targetId shouldBe seekerId
                }
            }

            test("Void met: end-step trigger lets player re-attach to a different creature") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Hylderblade")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 — initial equip target
                    .withCardOnBattlefield(1, "Towering Baloth") // 7/6 — re-attach target
                    .withCardOnBattlefield(2, "Devoted Hero") // 1/2 — Shock fodder for Void
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInHand(1, "Shock")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bladeId = game.findPermanent("Hylderblade")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val balothId = game.findPermanent("Towering Baloth")!!
                val heroId = game.findPermanent("Devoted Hero")!!
                val equipAbility = cardRegistry.getCard("Hylderblade")!!.script.activatedAbilities.first()

                // Equip Hylderblade onto Glory Seeker.
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = bladeId,
                        abilityId = equipAbility.id,
                        targets = listOf(ChosenTarget.Permanent(seekerId))
                    )
                )
                game.resolveStack()

                // Shock the opposing Devoted Hero — its death satisfies the Void condition.
                game.castSpell(1, "Shock", heroId)
                game.resolveStack()
                withClue("Devoted Hero should be dead") {
                    game.isInGraveyard(2, "Devoted Hero") shouldBe true
                }

                // Advance to end step so the Void trigger fires; expect a target prompt.
                game.passUntilPhase(Phase.ENDING, Step.END)
                withClue("Void trigger should put a target decision on the stack") {
                    game.hasPendingDecision() shouldBe true
                }

                // Re-attach to Towering Baloth.
                game.selectTargets(listOf(balothId))
                game.resolveStack()

                val attachedTo = game.state.getEntity(bladeId)!!.get<AttachedToComponent>()
                withClue("Hylderblade should now be attached to Towering Baloth") {
                    attachedTo shouldNotBe null
                    attachedTo!!.targetId shouldBe balothId
                }
            }
        }
    }
}
