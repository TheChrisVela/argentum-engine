package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Amazing Acrobatics.
 *
 * Card reference:
 * - Amazing Acrobatics ({1}{U}{U}): Instant
 *   "Choose one or both —
 *    • Counter target spell.
 *    • Tap one or two target creatures."
 */
class AmazingAcrobaticsScenarioTest : ScenarioTestBase() {

    init {
        context("Amazing Acrobatics — counter mode only") {

            test("counters the target spell and both spells resolve to graveyard") {
                // P2 is active so they can cast a creature at sorcery speed; P1 responds with Amazing Acrobatics
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withActivePlayer(2)
                    .withCardInHand(1, "Amazing Acrobatics")
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardInHand(2, "Grizzly Bears")
                    .withLandsOnBattlefield(2, "Forest", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Forest")
                    .build()

                // Opponent casts a spell; it lands on the stack
                game.castSpell(2, "Grizzly Bears")
                // P2 passes priority; P1 now has priority with Amazing Acrobatics in hand
                game.passPriority()

                val spellOnStack = game.state.stack.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Grizzly Bears"
                }!!

                val acrobaticsId = game.state.getHand(game.player1Id).find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Amazing Acrobatics"
                }!!

                // Cast Amazing Acrobatics choosing only the 'Counter target spell' mode (mode index 0)
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = acrobaticsId,
                        targets = listOf(ChosenTarget.Spell(spellOnStack)),
                        chosenModes = listOf(0),
                        modeTargetsOrdered = listOf(listOf(ChosenTarget.Spell(spellOnStack)))
                    )
                )
                withClue("Casting Amazing Acrobatics should succeed: ${result.error}") {
                    result.isSuccess shouldBe true
                }

                game.resolveStack()

                withClue("Grizzly Bears should be countered and in opponent's graveyard") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                withClue("Grizzly Bears should not be on the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
                withClue("Amazing Acrobatics should be in its owner's graveyard after resolution") {
                    game.isInGraveyard(1, "Amazing Acrobatics") shouldBe true
                }
            }
        }

        context("Amazing Acrobatics — both modes") {

            test("counters the spell and taps the creature when both modes are chosen") {
                // P2 is active: they cast a spell (on the stack) and also have a creature already on the battlefield
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withActivePlayer(2)
                    .withCardInHand(1, "Amazing Acrobatics")
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardInHand(2, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withLandsOnBattlefield(2, "Forest", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Forest")
                    .build()

                // P2 casts Grizzly Bears; it goes on the stack. P1 gets priority.
                game.castSpell(2, "Grizzly Bears")
                game.passPriority()

                val spellOnStack = game.state.stack.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Grizzly Bears"
                }!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val acrobaticsId = game.state.getHand(game.player1Id).find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Amazing Acrobatics"
                }!!

                // Cast Amazing Acrobatics choosing BOTH modes:
                //   mode 0 → counter Grizzly Bears (on stack)
                //   mode 1 → tap Glory Seeker (on battlefield)
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = acrobaticsId,
                        targets = listOf(ChosenTarget.Spell(spellOnStack), ChosenTarget.Permanent(seekerId)),
                        chosenModes = listOf(0, 1),
                        modeTargetsOrdered = listOf(
                            listOf(ChosenTarget.Spell(spellOnStack)),
                            listOf(ChosenTarget.Permanent(seekerId))
                        )
                    )
                )
                withClue("Casting Amazing Acrobatics (both modes) should succeed: ${result.error}") {
                    result.isSuccess shouldBe true
                }

                game.resolveStack()

                withClue("Grizzly Bears should be countered and in opponent's graveyard") {
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                withClue("Grizzly Bears should not be on the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
                withClue("Glory Seeker should be tapped") {
                    game.state.getEntity(seekerId)?.get<TappedComponent>() shouldBe TappedComponent
                }
                withClue("Amazing Acrobatics should be in its owner's graveyard after resolution") {
                    game.isInGraveyard(1, "Amazing Acrobatics") shouldBe true
                }
            }
        }

        context("Amazing Acrobatics — tap mode only") {

            test("taps both targeted creatures when two targets are chosen") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withActivePlayer(1)
                    .withCardInHand(1, "Amazing Acrobatics")
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .inPhase(com.wingedsheep.sdk.core.Phase.PRECOMBAT_MAIN, com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findPermanent("Grizzly Bears")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val acrobaticsId = game.state.getHand(game.player1Id).find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Amazing Acrobatics"
                }!!

                // Cast Amazing Acrobatics choosing only the 'Tap one or two target creatures' mode (mode index 1)
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = acrobaticsId,
                        targets = listOf(ChosenTarget.Permanent(bearsId), ChosenTarget.Permanent(seekerId)),
                        chosenModes = listOf(1),
                        modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(bearsId), ChosenTarget.Permanent(seekerId)))
                    )
                )
                withClue("Casting Amazing Acrobatics (tap mode) should succeed: ${result.error}") {
                    result.isSuccess shouldBe true
                }

                game.resolveStack()

                withClue("Amazing Acrobatics should be in its owner's graveyard after resolution") {
                    game.isInGraveyard(1, "Amazing Acrobatics") shouldBe true
                }
                withClue("Grizzly Bears should be tapped") {
                    game.state.getEntity(bearsId)?.get<TappedComponent>() shouldBe TappedComponent
                }
                withClue("Glory Seeker should be tapped") {
                    game.state.getEntity(seekerId)?.get<TappedComponent>() shouldBe TappedComponent
                }
            }

            test("taps exactly one creature when only one target is chosen") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withActivePlayer(1)
                    .withCardInHand(1, "Amazing Acrobatics")
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .inPhase(com.wingedsheep.sdk.core.Phase.PRECOMBAT_MAIN, com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findPermanent("Grizzly Bears")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val acrobaticsId = game.state.getHand(game.player1Id).find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Amazing Acrobatics"
                }!!

                // Cast Amazing Acrobatics targeting only Grizzly Bears (one target)
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = acrobaticsId,
                        targets = listOf(ChosenTarget.Permanent(bearsId)),
                        chosenModes = listOf(1),
                        modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(bearsId)))
                    )
                )
                withClue("Casting Amazing Acrobatics with one target should succeed: ${result.error}") {
                    result.isSuccess shouldBe true
                }

                game.resolveStack()

                withClue("Grizzly Bears should be tapped") {
                    game.state.getEntity(bearsId)?.get<TappedComponent>() shouldBe TappedComponent
                }
                withClue("Glory Seeker should remain untapped") {
                    game.state.getEntity(seekerId)?.get<TappedComponent>() shouldBe null
                }
            }

            test("cannot cast tap mode with zero targets (minCount=1 enforced)") {
                // No creatures on the battlefield — tap mode requires at least one
                // legal target per the 'one or two target creatures' wording.
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withActivePlayer(1)
                    .withCardInHand(1, "Amazing Acrobatics")
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .inPhase(com.wingedsheep.sdk.core.Phase.PRECOMBAT_MAIN, com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
                    .build()

                val acrobaticsId = game.state.getHand(game.player1Id).find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Amazing Acrobatics"
                }!!

                // Attempt to cast tap mode with zero targets — should fail per rule 601.2c
                // because the mode's target requirement has minCount=1.
                val result = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = acrobaticsId,
                        targets = emptyList(),
                        chosenModes = listOf(1),
                        modeTargetsOrdered = listOf(emptyList())
                    )
                )
                withClue("Casting Amazing Acrobatics tap mode with zero targets should fail") {
                    result.isSuccess shouldBe false
                }
            }
        }
    }
}
