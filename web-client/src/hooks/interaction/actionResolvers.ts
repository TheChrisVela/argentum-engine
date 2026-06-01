/**
 * Action resolver pipeline for useInteraction.
 *
 * The "tap creatures with total power N" selection (Crew N / Saddle N) is a standalone
 * single-phase flow handled directly here. Everything else goes through the pipeline
 * coordinator (pipelineSlice) which computes the full phase sequence and advances through it.
 */
import type { EntityId, LegalActionInfo } from '@/types'
import type { TapForPowerSelectionState } from '@/store/slices/types'
import { computePhases } from '@/store/slices/ui/pipelinePhases'
import { useGameStore } from '@/store/gameStore'

export interface ActionContext {
  selectCard: (id: EntityId | null) => void
  startTapForPowerSelection: (state: TapForPowerSelectionState) => void
  startPipeline: (actionInfo: LegalActionInfo) => void
}

// ---------------------------------------------------------------------------
// Standalone resolvers (not part of the pipeline)
// ---------------------------------------------------------------------------

/** Crew N (Vehicles) and Saddle N (Mounts) share the tap-for-power selection. */
function isTapForPowerAction(info: LegalActionInfo): boolean {
  return (
    (info.action.type === 'CrewVehicle' || info.action.type === 'SaddleMount') &&
    !!info.tapForPower &&
    !!info.tapForPowerCreatures &&
    info.tapForPowerCreatures.length > 0
  )
}

function resolveTapForPower(info: LegalActionInfo, ctx: ActionContext): void {
  const verb = info.action.type === 'SaddleMount' ? 'Saddle' : 'Crew'
  ctx.startTapForPowerSelection({
    actionInfo: info,
    verb,
    sourceName: info.description.replace(`${verb} `, ''),
    requiredPower: info.tapForPowerRequired ?? 0,
    selectedCreatures: [],
    validCreatures: info.tapForPowerCreatures!,
  })
  ctx.selectCard(null)
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Route an action through the appropriate handler. Returns true if the action
 * was handled (caller should not submit directly).
 */
export function resolveAction(actionInfo: LegalActionInfo, ctx: ActionContext): boolean {
  // Tap-for-power (Crew / Saddle) is standalone — not part of the pipeline
  if (isTapForPowerAction(actionInfo)) {
    resolveTapForPower(actionInfo, ctx)
    return true
  }
  // Everything else goes through the pipeline coordinator.
  // computePhases inside startPipeline decides what UI phases are needed;
  // if none, startPipeline submits directly.
  ctx.startPipeline(actionInfo)
  return true
}

/**
 * Returns true if the action requires interaction (selection UI) before it can
 * be submitted. Used by canAutoExecute / handleDoubleClick.
 */
export function needsInteraction(actionInfo: LegalActionInfo): boolean {
  if (isTapForPowerAction(actionInfo)) return true
  const { autoTapEnabled } = useGameStore.getState()
  return computePhases(actionInfo, { autoTapEnabled }).length > 0
}
