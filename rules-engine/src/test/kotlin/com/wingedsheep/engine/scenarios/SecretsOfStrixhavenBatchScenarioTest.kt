package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the four Secrets of Strixhaven cards added in this batch:
 *  - Honorbound Page // Forum's Favor (Prepared; the prepare spell grants +1/+0 and flying)
 *  - Stirring Honormancer (ETB: look at top X = creatures you control, one to hand, rest to graveyard)
 *  - Rubble Rouser (ETB loot; mana ability that pings each opponent for 1 "when you do")
 *  - Practiced Offense (counters on each creature target player controls + modal keyword + flashback)
 *
 * All four compose existing SDK primitives — these tests pin the composed behaviour.
 */
class SecretsOfStrixhavenBatchScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    private fun TestGame.findExileCopy(name: String): com.wingedsheep.sdk.model.EntityId? =
        state.getExile(player1Id).firstOrNull { id ->
            state.getEntity(id)?.get<CardComponent>()?.name == name
        }

    init {
        context("Honorbound Page // Forum's Favor") {

            test("the prepare-spell copy gives target creature +1/+0 and flying until end of turn") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Honorbound Page")
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                game.castSpell(1, "Honorbound Page")
                game.resolveStack()

                val page = game.findPermanent("Honorbound Page")!!
                withClue("Honorbound Page enters with first strike") {
                    projector.project(game.state).hasKeyword(page, Keyword.FIRST_STRIKE) shouldBe true
                }

                val bears = game.findPermanent("Grizzly Bears")!!
                val copyId = game.findExileCopy("Honorbound Page")
                withClue("Becoming prepared creates a Forum's Favor copy in exile") {
                    copyId shouldNotBe null
                }

                // Cast the prepare-spell copy (face 0) targeting Grizzly Bears.
                game.execute(
                    CastSpell(
                        game.player1Id,
                        copyId!!,
                        targets = listOf(ChosenTarget.Permanent(bears)),
                        faceIndex = 0,
                    )
                )
                game.resolveStack()

                val projected = projector.project(game.state)
                withClue("Grizzly Bears (base 2/2) gets +1/+0 → power 3") {
                    projected.getPower(bears) shouldBe 3
                }
                withClue("Grizzly Bears gains flying") {
                    projected.hasKeyword(bears, Keyword.FLYING) shouldBe true
                }
            }
        }

        context("Stirring Honormancer") {

            test("ETB looks at top X cards (X = creatures you control), one to hand, rest to graveyard") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Stirring Honormancer")
                    // {2}{W}{W/B}{B} needs white and black mana.
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    // Two other creatures already in play → X = 3 once the Honormancer resolves.
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(6) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(6) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                val handBefore = game.handSize(1)
                val graveBefore = game.state.getGraveyard(game.player1Id).size

                game.castSpell(1, "Stirring Honormancer")
                game.resolveStack()

                // Resolve the creature, let its ETB trigger go on the stack and resolve until the
                // pipeline pauses to let the controller pick one of the X looked-at cards.
                var pick: com.wingedsheep.engine.core.SelectCardsDecision? = null
                var safety = 0
                while (pick == null && safety++ < 10) {
                    val pending = game.state.pendingDecision
                    if (pending is com.wingedsheep.engine.core.SelectCardsDecision) {
                        pick = pending
                        break
                    }
                    if (game.state.priorityPlayerId != null) game.passPriority() else break
                }
                withClue("Stirring Honormancer should pause to choose a card to keep") {
                    pick shouldNotBe null
                }
                withClue("X = 3 cards are looked at (two Bears + the Honormancer)") {
                    pick!!.options.size shouldBe 3
                }
                game.selectCards(listOf(pick!!.options.first()))
                game.resolveStack()

                // X = 3 (two Bears + the Honormancer). One card → hand, the other two → graveyard.
                withClue("One looked-at card goes to hand (net +1 vs the card cast)") {
                    // Casting the Honormancer removed it from hand; the ETB then adds 1 card.
                    game.handSize(1) shouldBe handBefore - 1 + 1
                }
                withClue("The remaining looked-at cards (X-1 = 2) go to the graveyard") {
                    game.state.getGraveyard(game.player1Id).size shouldBe graveBefore + 2
                }
            }
        }

        context("Rubble Rouser") {

            test("the mana ability adds {R} and pings each opponent for 1") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Rubble Rouser", summoningSickness = false)
                    .withCardInGraveyard(1, "Forest")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                val rouser = game.findPermanent("Rubble Rouser")!!

                // Find the activated ability ({T}, exile a card from graveyard: Add {R} ...).
                val activate = game.getLegalActions(1).firstOrNull { la ->
                    val a = la.action
                    a is com.wingedsheep.engine.core.ActivateAbility && a.sourceId == rouser
                }
                withClue("The {T}, exile-from-graveyard mana ability should be offered") {
                    activate shouldNotBe null
                }

                game.execute(activate!!.action)
                game.resolveStack()

                withClue("Each opponent takes 1 damage from the reflexive trigger") {
                    game.getLifeTotal(2) shouldBe 19
                }
                withClue("The graveyard card was exiled as a cost") {
                    game.state.getGraveyard(game.player1Id).none {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Forest"
                    } shouldBe true
                }
            }
        }

        context("Practiced Offense") {

            test("puts a +1/+1 counter on each creature the target player controls and grants the chosen keyword") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Practiced Offense")
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                val bears = game.findPermanents("Grizzly Bears")
                bears.size shouldBe 2
                val handCard = game.state.getHand(game.player1Id).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Practiced Offense"
                }

                // Target: player 1 (counters on each creature they control) + first Bears (keyword).
                game.execute(
                    CastSpell(
                        game.player1Id,
                        handCard,
                        targets = listOf(
                            ChosenTarget.Player(game.player1Id),
                            ChosenTarget.Permanent(bears[0]),
                        ),
                    )
                )

                // Resolve, answering the "double strike or lifelink" choice as it surfaces.
                game.resolveStack()
                val pending = game.state.pendingDecision
                if (pending is ChooseOptionDecision) {
                    // Choose double strike (index 0).
                    game.submitDecision(OptionChosenResponse(pending.id, 0))
                    game.resolveStack()
                }

                withClue("Each creature the target player controls gets a +1/+1 counter") {
                    for (b in bears) {
                        val counters = game.state.getEntity(b)?.get<CountersComponent>()
                        (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
                    }
                }
                withClue("The target creature gains double strike") {
                    projector.project(game.state).hasKeyword(bears[0], Keyword.DOUBLE_STRIKE) shouldBe true
                }

                withClue("Practiced Offense is now in the graveyard and can be flashed back") {
                    game.state.getGraveyard(game.player1Id).any {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Practiced Offense"
                    } shouldBe true
                }
            }
        }
    }
}
