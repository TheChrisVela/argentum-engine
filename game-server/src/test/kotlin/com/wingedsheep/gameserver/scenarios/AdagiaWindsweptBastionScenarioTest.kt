package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Supertype
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Adagia, Windswept Bastion.
 *
 * Card reference:
 * - Adagia, Windswept Bastion: Land — Planet
 *   This land enters tapped.
 *   {T}: Add {W}.
 *   Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
 *   12+ | {3}{W}, {T}: Create a token that's a copy of target artifact or enchantment you control,
 *         except it's legendary. Activate only as a sorcery.
 */
class AdagiaWindsweptBastionScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Adagia 12+ ability — legendary token copy") {

            test("creates a legendary token copy of a non-legendary artifact you control") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Adagia, Windswept Bastion")
                    .withCardOnBattlefield(1, "Cryogen Relic")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val adagia = game.findPermanent("Adagia, Windswept Bastion")!!
                val relic = game.findPermanent("Cryogen Relic")!!

                // Pump Adagia up to 12 charge counters directly so we don't have to
                // route through Station (covered by SledgeClassSeedshipScenarioTest).
                game.state = game.state.updateEntity(adagia) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 12))
                }

                val cardDef = cardRegistry.getCard("Adagia, Windswept Bastion")!!
                // Activated abilities order: 0 = {T}: Add {W}, 1 = Station, 2 = 12+ token-copy
                val tokenAbility = cardDef.script.activatedAbilities[2]

                val before = game.state.getBattlefield(game.player1Id).toSet()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = adagia,
                        abilityId = tokenAbility.id,
                        targets = listOf(ChosenTarget.Permanent(relic)),
                        paymentStrategy = PaymentStrategy.AutoPay
                    )
                )

                withClue("Activation should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                game.resolveStack()

                val after = game.state.getBattlefield(game.player1Id).toSet()
                val newEntities = after - before
                withClue("A new token should have been created") {
                    newEntities.size shouldBe 1
                }

                val tokenId = newEntities.single()
                val tokenContainer = game.state.getEntity(tokenId)!!
                val tokenCard = tokenContainer.get<CardComponent>()!!

                withClue("New entity is marked as a token") {
                    (tokenContainer.get<TokenComponent>() != null) shouldBe true
                }
                withClue("Token has the same name as the copied artifact") {
                    tokenCard.name shouldBe "Cryogen Relic"
                }
                withClue("Token's typeLine carries the LEGENDARY supertype") {
                    (Supertype.LEGENDARY in tokenCard.typeLine.supertypes) shouldBe true
                }
                withClue("Projected state reports the token as legendary") {
                    stateProjector.project(game.state).isLegendary(tokenId) shouldBe true
                }

                // Original Cryogen Relic is unaffected (not legendary).
                val originalCard = game.state.getEntity(relic)!!.get<CardComponent>()!!
                withClue("Original artifact remains non-legendary") {
                    (Supertype.LEGENDARY in originalCard.typeLine.supertypes) shouldBe false
                }
            }

            test("ability is not available with fewer than 12 charge counters") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Adagia, Windswept Bastion")
                    .withCardOnBattlefield(1, "Cryogen Relic")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val adagia = game.findPermanent("Adagia, Windswept Bastion")!!
                val relic = game.findPermanent("Cryogen Relic")!!

                game.state = game.state.updateEntity(adagia) {
                    it.with(CountersComponent().withAdded(CounterType.CHARGE, 11))
                }

                val cardDef = cardRegistry.getCard("Adagia, Windswept Bastion")!!
                val tokenAbility = cardDef.script.activatedAbilities[2]

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = adagia,
                        abilityId = tokenAbility.id,
                        targets = listOf(ChosenTarget.Permanent(relic)),
                        paymentStrategy = PaymentStrategy.AutoPay
                    )
                )

                withClue("Activation should be rejected with only 11 charge counters") {
                    (result.error != null) shouldBe true
                }
            }
        }
    }
}
