package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Path of Ancestry.
 *
 * Card reference:
 *   Path of Ancestry — Land
 *     This land enters tapped.
 *     {T}: Add one mana of any color in your commander's color identity. When that
 *     mana is spent to cast a creature spell that shares a creature type with your
 *     commander, scry 1.
 *
 * Exercises:
 *   - The mana ability produces a restricted-mana entry under
 *     [ManaRestriction.AnySpend] carrying the
 *     [ManaSpellRider.ScryOnSharedTypeWithCommander] rider.
 *   - Spending that mana on a creature spell that shares a creature type with the
 *     controller's commander queues a scry decision on the stack above the spell.
 *   - Spending that mana on a creature that shares NO type with the commander
 *     produces no scry decision.
 *   - Spending that mana on a non-creature spell produces no scry decision.
 */
class PathOfAncestryScenarioTest : ScenarioTestBase() {

    init {
        context("Path of Ancestry — mana ability") {
            test("produces a restricted entry carrying the scry rider") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Path of Ancestry")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.designateCommanderInCommandZone(1, "Bello, Bard of the Brambles")

                val pathId = game.findPermanent("Path of Ancestry")!!
                val manaAbility = cardRegistry.getCard("Path of Ancestry")!!
                    .script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = pathId,
                        abilityId = manaAbility.id,
                        manaColorChoice = Color.GREEN,
                    )
                )

                withClue("Mana ability should resolve") { result.isSuccess shouldBe true }

                val pool = result.newState.getEntity(game.player1Id)?.get<ManaPoolComponent>()!!
                withClue("Pool should hold one restricted entry") {
                    pool.restrictedMana.size shouldBe 1
                }
                val entry = pool.restrictedMana.single()
                entry.color shouldBe Color.GREEN
                entry.restriction shouldBe ManaRestriction.AnySpend
                entry.riders shouldBe setOf(ManaSpellRider.ScryOnSharedTypeWithCommander(1))
            }
        }

        context("Path of Ancestry — scry trigger on creature cast") {
            test("casting a creature that shares a type with the commander queues a scry decision") {
                // Bello is a Raccoon Bard; Bark-Knuckle Boxer is a Raccoon — they share "Raccoon".
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Path of Ancestry")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInHand(1, "Bark-Knuckle Boxer")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.designateCommanderInCommandZone(1, "Bello, Bard of the Brambles")

                val pathId = game.findPermanent("Path of Ancestry")!!
                val manaAbility = cardRegistry.getCard("Path of Ancestry")!!
                    .script.activatedAbilities.first()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = pathId,
                        abilityId = manaAbility.id,
                        manaColorChoice = Color.GREEN,
                    )
                )

                // Cast Bark-Knuckle Boxer ({1}{G}). Restricted Path mana is preferred, so the
                // scry rider is consumed and queues the trigger above the spell on the stack.
                game.castSpell(1, "Bark-Knuckle Boxer")
                // Players pass priority; the scry trigger sits above the Boxer and resolves first.
                game.resolveStack()

                withClue("Scry trigger should pause for a player decision while resolving") {
                    game.hasPendingDecision() shouldBe true
                }
                withClue("Boxer should still be on the stack while scry resolves above it") {
                    val boxerOnStack = game.state.stack.any { entityId ->
                        game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Bark-Knuckle Boxer"
                    }
                    boxerOnStack shouldBe true
                }
            }

            test("casting a creature with no shared type produces no scry decision") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Path of Ancestry")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInHand(1, "Grizzly Bears") // Bear — no overlap with Raccoon Bard
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.designateCommanderInCommandZone(1, "Bello, Bard of the Brambles")

                val pathId = game.findPermanent("Path of Ancestry")!!
                val manaAbility = cardRegistry.getCard("Path of Ancestry")!!
                    .script.activatedAbilities.first()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = pathId,
                        abilityId = manaAbility.id,
                        manaColorChoice = Color.GREEN,
                    )
                )

                game.castSpell(1, "Grizzly Bears")

                withClue("Rider is consumed but condition fails — no decision should be pending") {
                    game.hasPendingDecision() shouldBe false
                }
            }

            test("casting a non-creature spell with Path mana produces no scry decision") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Path of Ancestry")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInHand(1, "Cultivate") // sorcery, {2}{G}
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.designateCommanderInCommandZone(1, "Bello, Bard of the Brambles")

                val pathId = game.findPermanent("Path of Ancestry")!!
                val manaAbility = cardRegistry.getCard("Path of Ancestry")!!
                    .script.activatedAbilities.first()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = pathId,
                        abilityId = manaAbility.id,
                        manaColorChoice = Color.GREEN,
                    )
                )

                game.castSpell(1, "Cultivate")

                withClue("Non-creature spell never triggers the scry rider") {
                    val pendingScry = game.state.pendingDecision
                    pendingScry shouldBe null
                }
            }
        }
    }

    /**
     * Designate an existing or freshly-created card as the player's commander, placing it in
     * the command zone and registering it on the player. Mirrors the helper in
     * [ArcaneSignetScenarioTest] — tests in non-real-game scenarios don't run the
     * [com.wingedsheep.engine.core.GameInitializer] commander setup path.
     */
    private fun TestGame.designateCommanderInCommandZone(playerNumber: Int, commanderName: String) {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        val def = cardRegistry.getCard(commanderName)
            ?: error("Card not found in registry: $commanderName")

        val commanderId = com.wingedsheep.sdk.model.EntityId.of("commander-$commanderName")
        val cardComponent = CardComponent(
            cardDefinitionId = def.name,
            name = def.name,
            manaCost = def.manaCost,
            typeLine = def.typeLine,
            oracleText = def.oracleText,
            colors = def.colors,
            baseKeywords = def.keywords,
            baseFlags = def.flags,
            baseStats = def.creatureStats,
            ownerId = playerId,
            spellEffect = def.spellEffect,
            imageUri = def.metadata.imageUri,
        )
        val container = com.wingedsheep.engine.state.ComponentContainer.of(
            cardComponent,
            com.wingedsheep.engine.state.components.identity.OwnerComponent(playerId),
            CommanderComponent(ownerId = playerId),
        )
        state = state.withEntity(commanderId, container)
        state = state.addToZone(
            com.wingedsheep.engine.state.ZoneKey(playerId, com.wingedsheep.sdk.core.Zone.COMMAND),
            commanderId,
        )
        state = state.updateEntity(playerId) { c ->
            val existing = c.get<CommanderRegistryComponent>()
            val ids = (existing?.commanderIds ?: emptyList()) + commanderId
            c.with(CommanderRegistryComponent(ids))
        }
    }

}
