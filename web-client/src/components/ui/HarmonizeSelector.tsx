import { useMemo } from 'react'
import { useGameStore } from '@/store/gameStore.ts'
import { useViewingPlayer } from '@/store/selectors'
import type { ClientManaPool } from '@/types/gameState'
import { parseManaCost, getRemainingCostSymbols } from '@/utils/manaCost'
import { ManaSymbol } from './ManaSymbols'

/**
 * Subtract the player's floating mana from [symbols] — colored pips first, then generic —
 * returning what's still owed. Mirrors the server autoPay order so the affordability check
 * matches the actual payment.
 */
function applyManaPool(symbols: string[], pool: ClientManaPool | undefined): string[] {
  if (!pool) return symbols
  const remaining = [...symbols]
  const available: Record<string, number> = {
    W: pool.white, U: pool.blue, B: pool.black, R: pool.red, G: pool.green, C: pool.colorless,
  }
  for (const pip of ['W', 'U', 'B', 'R', 'G', 'C']) {
    while (available[pip]! > 0) {
      const idx = remaining.indexOf(pip)
      if (idx < 0) break
      remaining.splice(idx, 1)
      available[pip]!--
    }
  }
  let generic = available.W! + available.U! + available.B! + available.R! + available.G! + available.C!
  while (generic > 0) {
    const idx = remaining.findIndex((s) => /^\d+$/.test(s))
    if (idx < 0) break
    const value = parseInt(remaining[idx]!, 10)
    if (value > 1) remaining[idx] = String(value - 1)
    else remaining.splice(idx, 1)
    generic--
  }
  return remaining
}

/** Total mana value of the remaining cost (generic = its value, colored pips = 1 each). */
function totalManaNeeded(symbols: string[]): number {
  let total = 0
  for (const s of symbols) {
    const num = parseInt(s, 10)
    total += isNaN(num) ? 1 : num
  }
  return total
}

/**
 * Floating HUD bar for the Harmonize creature-tap step. The player may tap one creature
 * (selected directly on the battlefield) to reduce the generic harmonize cost by its power;
 * {X} is already expanded into the displayed cost. Tapping is optional — "Cast" stays
 * enabled whenever the (possibly-reduced) cost is affordable from lands + floating mana.
 */
export function HarmonizeSelector() {
  const harmonizeSelectionState = useGameStore((state) => state.harmonizeSelectionState)
  const cancelHarmonizeSelection = useGameStore((state) => state.cancelHarmonizeSelection)
  const confirmHarmonizeSelection = useGameStore((state) => state.confirmHarmonizeSelection)
  const viewingPlayer = useViewingPlayer()
  const manaPool = viewingPlayer?.manaPool

  const originalSymbols = useMemo(
    () => (harmonizeSelectionState ? parseManaCost(harmonizeSelectionState.manaCost) : []),
    [harmonizeSelectionState?.manaCost],
  )

  const reduction = useMemo(() => {
    if (!harmonizeSelectionState?.selectedCreature) return 0
    return (
      harmonizeSelectionState.validCreatures.find(
        (c) => c.entityId === harmonizeSelectionState.selectedCreature,
      )?.power ?? 0
    )
  }, [harmonizeSelectionState?.selectedCreature, harmonizeSelectionState?.validCreatures])

  const remainingSymbols = useMemo(
    () => getRemainingCostSymbols(originalSymbols, reduction),
    [originalSymbols, reduction],
  )

  const symbolsAfterPool = useMemo(
    () => applyManaPool(remainingSymbols, manaPool),
    [remainingSymbols, manaPool],
  )

  if (!harmonizeSelectionState) return null

  const { cardName, selectedCreature, actionInfo } = harmonizeSelectionState
  const manaNeeded = totalManaNeeded(symbolsAfterPool)
  const manaFromSources = (actionInfo.availableManaSources ?? []).reduce((sum, s) => {
    if (s.entityId && s.entityId === selectedCreature) return sum // tapped for harmonize, not mana
    return sum + (s.manaAmount ?? 1)
  }, 0)
  const canAfford = manaNeeded <= manaFromSources

  return (
    <div style={styles.bar}>
      <span style={styles.label}>
        Harmonize <strong>{cardName}</strong>
      </span>
      <span style={styles.divider} />
      <span style={styles.hint}>
        {selectedCreature ? `Tapping for ${reduction} generic` : 'Tap a creature to reduce (optional)'}
      </span>
      <span style={styles.divider} />
      <span style={styles.costLabel}>Cost:</span>
      <div style={styles.manaSymbols}>
        {originalSymbols.map((symbol, i) => (
          <ManaSymbol key={i} symbol={symbol} size={18} />
        ))}
      </div>
      {reduction > 0 && (
        <>
          <span style={styles.arrow}>→</span>
          <div style={styles.manaSymbols}>
            {remainingSymbols.length > 0 ? (
              remainingSymbols.map((symbol, i) => <ManaSymbol key={i} symbol={symbol} size={18} />)
            ) : (
              <span style={styles.freeCast}>Free!</span>
            )}
          </div>
        </>
      )}
      <span style={styles.divider} />
      <button onClick={cancelHarmonizeSelection} style={styles.cancelButton}>
        Cancel
      </button>
      <button
        onClick={canAfford ? confirmHarmonizeSelection : undefined}
        style={canAfford ? styles.confirmButton : styles.confirmButtonDisabled}
      >
        Cast
      </button>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  bar: {
    position: 'absolute',
    bottom: 12,
    left: '50%',
    transform: 'translateX(-50%)',
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: '10px 20px',
    backgroundColor: 'rgba(20, 20, 40, 0.95)',
    border: '2px solid #4a4a6a',
    borderRadius: 10,
    boxShadow: '0 4px 20px rgba(0, 0, 0, 0.6)',
    zIndex: 1500,
    whiteSpace: 'nowrap',
  },
  label: { color: '#ccc', fontSize: 14 },
  hint: { color: '#9a9', fontSize: 12 },
  divider: { width: 1, height: 20, backgroundColor: '#4a4a6a' },
  costLabel: { color: '#888', fontSize: 13 },
  manaSymbols: { display: 'flex', alignItems: 'center', gap: 3 },
  arrow: { color: '#666', fontSize: 14 },
  freeCast: { color: '#4caf50', fontWeight: 'bold', fontSize: 13 },
  cancelButton: {
    padding: '6px 14px', fontSize: 13, backgroundColor: '#444', color: '#fff',
    border: 'none', borderRadius: 6, cursor: 'pointer',
  },
  confirmButton: {
    padding: '6px 14px', fontSize: 13, backgroundColor: '#0066cc', color: '#fff',
    border: 'none', borderRadius: 6, cursor: 'pointer',
  },
  confirmButtonDisabled: {
    padding: '6px 14px', fontSize: 13, backgroundColor: '#333', color: '#666',
    border: 'none', borderRadius: 6, cursor: 'not-allowed',
  },
}
