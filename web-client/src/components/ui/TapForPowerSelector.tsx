import { useMemo } from 'react'
import { useGameStore } from '@/store/gameStore.ts'

/**
 * Compact floating HUD bar for the "tap creatures with total power N" selection,
 * shared by Crew N (Vehicles) and Saddle N (Mounts). Shows power progress and
 * confirm/cancel buttons while creatures are selected directly on the battlefield.
 */
export function TapForPowerSelector() {
  const selection = useGameStore((state) => state.tapForPowerSelectionState)
  const cancelSelection = useGameStore((state) => state.cancelTapForPowerSelection)
  const confirmSelection = useGameStore((state) => state.confirmTapForPowerSelection)

  const selectedPower = useMemo(() => {
    if (!selection) return 0
    const { selectedCreatures, validCreatures } = selection
    let total = 0
    for (const id of selectedCreatures) {
      const creature = validCreatures.find((c) => c.entityId === id)
      if (creature) total += creature.power
    }
    return total
  }, [selection])

  if (!selection) return null

  const { verb, sourceName, requiredPower, selectedCreatures } = selection
  const canConfirm = selectedPower >= requiredPower

  return (
    <div style={styles.bar}>
      <span style={styles.label}>
        {verb} <strong>{sourceName}</strong>
      </span>
      <span style={styles.divider} />
      <span style={styles.powerInfo}>
        Power:&nbsp;
        <strong style={{ color: canConfirm ? '#4caf50' : '#ff9800' }}>
          {selectedPower}
        </strong>
        &nbsp;/&nbsp;{requiredPower}
      </span>
      <span style={styles.count}>
        ({selectedCreatures.length} creature{selectedCreatures.length !== 1 ? 's' : ''})
      </span>
      <span style={styles.divider} />
      <button onClick={cancelSelection} style={styles.cancelButton}>
        Cancel
      </button>
      <button
        onClick={confirmSelection}
        disabled={!canConfirm}
        style={{
          ...styles.confirmButton,
          opacity: canConfirm ? 1 : 0.5,
          cursor: canConfirm ? 'pointer' : 'not-allowed',
        }}
      >
        Confirm {verb}
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
    gap: 12,
    padding: '10px 20px',
    backgroundColor: 'rgba(20, 20, 40, 0.95)',
    border: '2px solid #4a4a6a',
    borderRadius: 10,
    boxShadow: '0 4px 20px rgba(0, 0, 0, 0.6)',
    zIndex: 1500,
    whiteSpace: 'nowrap',
  },
  label: {
    color: '#ccc',
    fontSize: 14,
  },
  divider: {
    width: 1,
    height: 20,
    backgroundColor: '#4a4a6a',
  },
  powerInfo: {
    color: '#aaa',
    fontSize: 14,
  },
  count: {
    color: '#666',
    fontSize: 12,
  },
  cancelButton: {
    padding: '6px 14px',
    fontSize: 13,
    backgroundColor: '#444',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    cursor: 'pointer',
  },
  confirmButton: {
    padding: '6px 14px',
    fontSize: 13,
    backgroundColor: '#0066cc',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    cursor: 'pointer',
  },
}
