package com.wingedsheep.engine.mechanics.sba.creature

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.mechanics.combat.CombatRemovalHelper
import com.wingedsheep.engine.mechanics.sba.SbaOrder
import com.wingedsheep.engine.mechanics.sba.StateBasedActionCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent

/**
 * CR 506.4 — "A permanent is removed from combat if … its controller changes …".
 *
 * Walks every creature with [AttackingComponent] or [BlockingComponent] and compares its
 * projected controller (which already reflects every Layer 2 control-changing effect, plus
 * Old Man of the Sea's post-Layer-7 power gate) to the player it should be fighting for:
 *
 * - Attackers must remain controlled by the active player (the only player who has
 *   declared attackers this turn). A projected controller that is no longer the active
 *   player → removed from combat.
 * - Blockers must remain controlled by a non-active player (the defender who declared
 *   them). A projected controller that has become the active player → removed from combat.
 *
 * The two-player approximation is sufficient for current scope; multi-player formats
 * can extend this once they're modelled. Removal uses [CombatRemovalHelper] so dependent
 * `BlockedComponent` / `BlockingComponent` references stay consistent.
 */
class ControlChangedRemovesFromCombatCheck : StateBasedActionCheck {
    override val name = "506.4 Controller-Changed Combat Removal"
    override val order = SbaOrder.CONTROL_CHANGED_COMBAT

    override fun check(state: GameState): ExecutionResult {
        val activePlayerId = state.activePlayerId
        val projected = state.projectedState

        val toRemove = mutableListOf<com.wingedsheep.sdk.model.EntityId>()
        for (entityId in state.getBattlefield()) {
            val container = state.getEntity(entityId) ?: continue
            val isAttacking = container.has<AttackingComponent>()
            val isBlocking = container.has<BlockingComponent>()
            if (!isAttacking && !isBlocking) continue

            val controllerId = projected.getController(entityId) ?: continue
            val mismatched = when {
                isAttacking -> controllerId != activePlayerId
                isBlocking -> controllerId == activePlayerId
                else -> false
            }
            if (mismatched) toRemove.add(entityId)
        }

        if (toRemove.isEmpty()) return ExecutionResult.success(state)

        var newState = state
        for (entityId in toRemove) {
            newState = CombatRemovalHelper.removeFromCombat(newState, entityId)
        }
        return ExecutionResult.success(newState)
    }
}
