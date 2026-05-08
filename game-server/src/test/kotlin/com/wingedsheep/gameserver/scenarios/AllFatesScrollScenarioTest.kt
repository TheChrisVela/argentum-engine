package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for All-Fates Scroll's "differently named lands" draw ability.
 *
 * Card reference:
 * - All-Fates Scroll ({3}): Artifact
 *   "{T}: Add one mana of any color."
 *   "{7}, {T}, Sacrifice this artifact: Draw X cards, where X is the number of differently
 *    named lands you control."
 */
class AllFatesScrollScenarioTest : ScenarioTestBase() {

    private fun addColorlessMana(game: TestGame, amount: Int) {
        game.state = game.state.updateEntity(game.player1Id) { container ->
            container.with(ManaPoolComponent(colorless = amount))
        }
    }

    private fun activateScrollSacrifice(game: TestGame) {
        val scroll = game.findPermanent("All-Fates Scroll")!!
        val cardDef = cardRegistry.getCard("All-Fates Scroll")!!
        // Second activated ability is the {7}, {T}, Sacrifice draw ability.
        val ability = cardDef.script.activatedAbilities[1]
        val result = game.execute(
            ActivateAbility(
                playerId = game.player1Id,
                sourceId = scroll,
                abilityId = ability.id
            )
        )
        withClue("Activation should succeed: ${result.error}") {
            result.error shouldBe null
        }
    }

    init {
        context("All-Fates Scroll — Draw X cards") {
            test("draws 1 per distinct land name (5 colors of basics = 5 cards)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "All-Fates Scroll")
                    .withCardOnBattlefield(1, "Plains")
                    .withCardOnBattlefield(1, "Island")
                    .withCardOnBattlefield(1, "Swamp")
                    .withCardOnBattlefield(1, "Mountain")
                    .withCardOnBattlefield(1, "Forest")
                    // Library cards to draw — must have at least 5 to avoid drawing from empty
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                addColorlessMana(game, 7)
                val handBefore = game.handSize(1)

                activateScrollSacrifice(game)
                game.resolveStack()

                withClue("Player should have drawn 5 cards (one per distinct basic land name)") {
                    game.handSize(1) shouldBe handBefore + 5
                }
                withClue("All-Fates Scroll should have been sacrificed") {
                    game.isOnBattlefield("All-Fates Scroll") shouldBe false
                }
            }

            test("counts each distinct name once, not each copy") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "All-Fates Scroll")
                    // Five Plains and one Island = 2 distinct names
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withCardOnBattlefield(1, "Island")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                addColorlessMana(game, 7)
                val handBefore = game.handSize(1)

                activateScrollSacrifice(game)
                game.resolveStack()

                withClue("Should draw 2 cards — Plains and Island count once each") {
                    game.handSize(1) shouldBe handBefore + 2
                }
            }

            test("draws zero when controlling no lands") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "All-Fates Scroll")
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                addColorlessMana(game, 7)
                val handBefore = game.handSize(1)

                activateScrollSacrifice(game)
                game.resolveStack()

                withClue("Should draw 0 cards — no lands controlled") {
                    game.handSize(1) shouldBe handBefore
                }
            }

            test("does not count opponent's lands") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "All-Fates Scroll")
                    .withCardOnBattlefield(1, "Plains")
                    // Opponent has differently named lands but they don't count
                    .withCardOnBattlefield(2, "Island")
                    .withCardOnBattlefield(2, "Swamp")
                    .withCardOnBattlefield(2, "Mountain")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                addColorlessMana(game, 7)
                val handBefore = game.handSize(1)

                activateScrollSacrifice(game)
                game.resolveStack()

                withClue("Should draw 1 — only the controller's Plains counts") {
                    game.handSize(1) shouldBe handBefore + 1
                }
            }
        }
    }
}
