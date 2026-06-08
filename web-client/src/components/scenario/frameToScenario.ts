/**
 * Convert a replay frame (a spectator-view {@link ClientGameState}) into a {@link ScenarioSpec}
 * for the "Share this frame as a scenario" action.
 *
 * Replays are *spectator* snapshots, so hidden zones (hands, library contents) are masked —
 * only their sizes are known. A frame→scenario therefore captures the **public** board:
 * battlefield permanents (with tapped / counters / aura attachment), graveyards, exiles, life
 * totals, and the phase/active-player. Hands and libraries start empty; the user fills them in
 * the builder. This is a documented limitation, surfaced in the UI.
 */
import type { ClientGameState, ClientCard } from '@/types/gameState'
import { ZoneType } from '@/types/enums'
import type { ScenarioBattlefieldCard, ScenarioPlayerConfig, ScenarioSpec } from './types'

function countersOf(card: ClientCard): Record<string, number> | undefined {
  const out: Record<string, number> = {}
  for (const [type, n] of Object.entries(card.counters)) {
    if (typeof n === 'number' && n > 0) out[type] = n
  }
  return Object.keys(out).length ? out : undefined
}

function publicConfigFor(gameState: ClientGameState, playerId: string): ScenarioPlayerConfig {
  const cardsById = gameState.cards
  const allCards = Object.values(cardsById)
  const player = gameState.players.find((p) => p.playerId === playerId)

  const battlefield: ScenarioBattlefieldCard[] = []
  const graveyard: string[] = []
  const exile: string[] = []

  for (const card of allCards) {
    const z = card.zone
    if (!z) continue
    if (z.zoneType === ZoneType.BATTLEFIELD && card.controllerId === playerId) {
      const entry: ScenarioBattlefieldCard = { name: card.name }
      if (card.isTapped) entry.tapped = true
      const counters = countersOf(card)
      if (counters) entry.counters = counters
      if (card.attachedTo) {
        const host = cardsById[card.attachedTo]
        if (host) entry.attachedTo = host.name
      }
      battlefield.push(entry)
    } else if (z.zoneType === ZoneType.GRAVEYARD && z.ownerId === playerId) {
      graveyard.push(card.name)
    } else if (z.zoneType === ZoneType.EXILE && card.ownerId === playerId) {
      exile.push(card.name)
    }
  }

  // Order auras after their hosts so the backend's name-based attachment wiring resolves.
  battlefield.sort((a, b) => (a.attachedTo ? 1 : 0) - (b.attachedTo ? 1 : 0))

  const cfg: ScenarioPlayerConfig = { lifeTotal: player?.life ?? 20 }
  if (battlefield.length) cfg.battlefield = battlefield
  if (graveyard.length) cfg.graveyard = graveyard
  if (exile.length) cfg.exile = exile
  return cfg
}

export function frameToScenario(
  gameState: ClientGameState,
  player1Id: string | null,
  player2Id: string | null,
  player1Name: string,
  player2Name: string,
): ScenarioSpec {
  const p1 = player1Id ?? gameState.players[0]?.playerId ?? null
  const p2 = player2Id ?? gameState.players[1]?.playerId ?? null
  const activeIsP1 = gameState.activePlayerId === p1

  return {
    player1Name,
    player2Name,
    player1: p1 ? publicConfigFor(gameState, p1) : { lifeTotal: 20 },
    player2: p2 ? publicConfigFor(gameState, p2) : { lifeTotal: 20 },
    phase: String(gameState.currentPhase),
    activePlayer: activeIsP1 ? 1 : 2,
    mode: 'SELF',
  }
}
