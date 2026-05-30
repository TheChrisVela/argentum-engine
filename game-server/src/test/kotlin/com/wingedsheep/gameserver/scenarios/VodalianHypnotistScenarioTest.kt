package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Vodalian Hypnotist:
 * "{2}{B}, {T}: Target player discards a card. Activate only as a sorcery."
 */
class VodalianHypnotistScenarioTest : ScenarioTestBase() {

    init {
        context("Vodalian Hypnotist discard ability") {

            test("forces target player to discard at sorcery speed") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Vodalian Hypnotist")
                    .withLandsOnBattlefield(1, "Swamp", 3) // pay {2}{B}
                    .withCardInHand(2, "Forest")           // opponent's only card -> auto-discard
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hypnotistId = game.findPermanent("Vodalian Hypnotist")!!
                val ability = cardRegistry.getCard("Vodalian Hypnotist")!!.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = hypnotistId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Player(game.player2Id))
                    )
                )
                withClue("Activation should be legal at sorcery speed during main phase: ${result.error}") {
                    result.error shouldBe null
                }

                game.resolveStack()

                withClue("Opponent should have discarded their only card") {
                    game.handSize(2) shouldBe 0
                }
            }

            test("cannot be activated at instant speed (opponent's turn)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Vodalian Hypnotist")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInHand(2, "Forest")
                    .withActivePlayer(2) // opponent's turn — not a legal sorcery-speed window for player 1
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hypnotistId = game.findPermanent("Vodalian Hypnotist")!!
                val ability = cardRegistry.getCard("Vodalian Hypnotist")!!.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = hypnotistId,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Player(game.player2Id))
                    )
                )
                withClue("Sorcery-speed ability should be rejected on the opponent's turn") {
                    result.error shouldNotBe null
                }
            }
        }
    }
}
