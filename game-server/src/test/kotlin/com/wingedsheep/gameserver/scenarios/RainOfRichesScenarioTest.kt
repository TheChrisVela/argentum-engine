package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Rain of Riches (NCC).
 *
 * 1. ETB creates two Treasure tokens.
 * 2. The first spell each turn paid for with treasure mana has cascade. The second
 *    treasure-paid spell that turn does NOT cascade.
 *
 * The cascade trigger condition is
 * [com.wingedsheep.sdk.scripting.conditions.IsFirstSpellPaidWithTreasureManaCastThisTurn],
 * which reads the controller's per-turn `CastSpellRecord` history. The newly-added
 * `paidWithTreasureMana` field on `CastSpellRecord` is what makes "first of its kind"
 * resolvable. Tests follow the AlchemistsTalentTest pattern of seeding the mana pool
 * with `treasureMana = N` to simulate having just sacrificed a Treasure for that mana.
 */
class RainOfRichesScenarioTest : ScenarioTestBase() {

    init {
        context("Rain of Riches — ETB") {
            test("creates two Treasure tokens when it enters the battlefield") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Rain of Riches")
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Rain of Riches")
                cast.error shouldBe null
                game.resolveStack()

                val treasures = game.state.getBattlefield()
                    .mapNotNull { game.state.getEntity(it) }
                    .count { it.get<CardComponent>()?.name == "Treasure" }
                withClue("Two Treasure tokens should be on the battlefield") {
                    treasures shouldBe 2
                }
            }
        }

        context("Rain of Riches — cascade on first treasure-paid spell") {

            test("first spell paid with treasure mana cascades") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Rain of Riches")
                    .withCardInHand(1, "Hoarder's Overflow")
                    // Library top → bottom: Mountain (skipped), Elvish Pioneer (mv 1, less
                    // than Hoarder's Overflow mv 2 — cascade hit), then buffer cards.
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Elvish Pioneer")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Seed the player's pool with two red mana, both tagged as treasure mana,
                // simulating having just activated two Treasure tokens for {R}{R}.
                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(ManaPoolComponent(red = 2, treasureMana = 2))
                }

                val cast = game.castSpell(1, "Hoarder's Overflow")
                withClue("Hoarder's Overflow should cast: ${cast.error}") {
                    cast.error shouldBe null
                }

                val records = game.state.spellsCastThisTurnByPlayer[game.player1Id] ?: emptyList()
                withClue("CastSpellRecord should mark this spell as paid with treasure mana") {
                    records.lastOrNull()?.paidWithTreasureMana shouldBe true
                }

                // First resolve hits the cascade trigger (pauses on the may-cast decision).
                game.resolveStack()
                withClue("Cascade should pause for the may-cast yes/no decision") {
                    (game.state.pendingDecision as? YesNoDecision) shouldNotBe null
                }
                game.answerYesNo(true)
                // Resolve the cascade-cast spell and the original Scorching Spear.
                game.resolveStack()

                val battlefield = game.state.getZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD))
                val pioneerOnBoard = battlefield.any { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Elvish Pioneer"
                }
                withClue("Elvish Pioneer should be on the battlefield (cascade hit cast for free)") {
                    pioneerOnBoard shouldBe true
                }
                val exile = game.state.getZone(ZoneKey(game.player1Id, Zone.EXILE))
                withClue("Exile should be empty — cascade hit was cast, skipped lands bottomed") {
                    exile shouldHaveSize 0
                }
            }

            test("second treasure-paid spell same turn does NOT cascade") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Rain of Riches")
                    .withCardsInHand(1, "Hoarder's Overflow", 2)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Elvish Pioneer")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // First treasure-paid cast → triggers cascade.
                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(ManaPoolComponent(red = 2, treasureMana = 2))
                }
                game.castSpell(1, "Hoarder's Overflow").error shouldBe null
                game.resolveStack()
                // Decline the cascade may-cast and resolve out the first spell.
                game.answerYesNo(false)
                game.resolveStack()

                val librarySizeBefore = game.state.getLibrary(game.player1Id).size

                // Seed the pool again with treasure-tagged red mana for the second cast.
                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(ManaPoolComponent(red = 2, treasureMana = 2))
                }
                game.castSpell(1, "Hoarder's Overflow").error shouldBe null
                game.resolveStack()

                withClue("Second treasure-paid spell must not trigger cascade — no may-cast pause") {
                    game.state.pendingDecision shouldBe null
                }
                withClue("Library size should be unchanged — cascade should not have walked the library") {
                    game.state.getLibrary(game.player1Id).size shouldBe librarySizeBefore
                }
            }

            test("spell cast WITHOUT treasure mana does not trigger Rain of Riches at all") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Rain of Riches")
                    .withCardInHand(1, "Hoarder's Overflow")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Elvish Pioneer")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val librarySizeBefore = game.state.getLibrary(game.player1Id).size

                game.castSpell(1, "Hoarder's Overflow").error shouldBe null
                game.resolveStack()

                withClue("No cascade should fire — mana came from Mountains, not Treasures") {
                    game.state.pendingDecision shouldBe null
                }
                withClue("Library walk should not have happened") {
                    game.state.getLibrary(game.player1Id).size shouldBe librarySizeBefore
                }
            }
        }
    }
}
