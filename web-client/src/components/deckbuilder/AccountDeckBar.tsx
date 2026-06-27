/**
 * Deckbuilder toolbar control for cloud-saved decks. When signed in, lets you save the current deck
 * to your account and load/delete your saved decks; when anonymous, offers a sign-in prompt. Kept
 * self-contained so the (large) DeckbuilderPage only has to provide a build + load callback.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import {
  type DeckSummary,
  deleteDeck as apiDeleteDeck,
  getDeck,
  listDecks,
  saveDeck,
} from '@/api/account'
import { LoginModal } from '@/components/auth/LoginModal'
import type { SharedDeck } from '@/components/deckbuilder/shareDeck'
import { useAuthStore } from '@/store/authStore'

interface AccountDeckBarProps {
  /** Build a SharedDeck from the current builder state, or null if the deck is empty. */
  buildSharedDeck: () => SharedDeck | null
  /** Apply a loaded SharedDeck into the builder. */
  onLoad: (shared: SharedDeck) => void
  className?: string
}

export function AccountDeckBar({ buildSharedDeck, onLoad, className }: AccountDeckBarProps) {
  const status = useAuthStore((s) => s.status)
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const init = useAuthStore((s) => s.init)
  const [loginOpen, setLoginOpen] = useState(false)
  const [listOpen, setListOpen] = useState(false)
  const [decks, setDecks] = useState<DeckSummary[]>([])
  const [feedback, setFeedback] = useState<string | null>(null)

  useEffect(() => {
    if (status === 'idle') void init()
  }, [status, init])

  const refresh = () => {
    void listDecks().then(setDecks).catch(() => setDecks([]))
  }

  useEffect(() => {
    if (listOpen) refresh()
  }, [listOpen])

  const flash = (msg: string) => {
    setFeedback(msg)
    window.setTimeout(() => setFeedback(null), 2000)
  }

  const handleSave = async () => {
    const shared = buildSharedDeck()
    if (!shared) {
      flash('Deck is empty')
      return
    }
    try {
      await saveDeck(shared)
      flash('Saved!')
      if (listOpen) refresh()
    } catch {
      flash('Save failed')
    }
  }

  const handleLoad = async (id: number) => {
    try {
      const detail = await getDeck(id)
      onLoad(detail.deck)
      setListOpen(false)
    } catch {
      flash('Load failed')
    }
  }

  const handleDelete = async (id: number) => {
    await apiDeleteDeck(id)
    setDecks((prev) => prev.filter((d) => d.id !== id))
  }

  // No accounts subsystem on this server → no cloud-save controls at all.
  if (!accountsEnabled) return null

  if (status !== 'authenticated') {
    return (
      <div className={className}>
        <button type="button" style={styles.button} onClick={() => setLoginOpen(true)}>
          Sign in to save
        </button>
        <LoginModal open={loginOpen} onClose={() => setLoginOpen(false)} />
      </div>
    )
  }

  return (
    <div className={className} style={styles.wrap}>
      <button type="button" style={styles.button} onClick={() => void handleSave()}>
        {feedback ?? 'Save online'}
      </button>
      <button type="button" style={styles.button} onClick={() => setListOpen((v) => !v)}>
        My decks
      </button>
      {listOpen && (
        <div style={styles.popover}>
          {decks.length === 0 ? (
            <div style={styles.empty}>No saved decks yet.</div>
          ) : (
            <ul style={styles.list}>
              {decks.map((deck) => (
                <li key={deck.id} style={styles.item}>
                  <button type="button" style={styles.itemName} onClick={() => void handleLoad(deck.id)}>
                    {deck.name}
                    {deck.format ? <span style={styles.format}> · {deck.format}</span> : null}
                  </button>
                  <button type="button" style={styles.delete} onClick={() => void handleDelete(deck.id)}>
                    ×
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { position: 'relative', display: 'inline-flex', gap: 8 },
  button: {
    padding: '6px 12px',
    borderRadius: 8,
    border: '1px solid #2a2a3e',
    backgroundColor: '#1a1a2e',
    color: '#fff',
    fontSize: 13,
    cursor: 'pointer',
  },
  popover: {
    position: 'absolute',
    top: '100%',
    right: 0,
    marginTop: 6,
    minWidth: 240,
    maxHeight: 320,
    overflowY: 'auto',
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 10,
    padding: 8,
    zIndex: 1500,
    boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
  },
  empty: { color: '#888', fontSize: 13, padding: 8 },
  list: { listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 4 },
  item: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  itemName: {
    flex: 1,
    background: 'none',
    border: 'none',
    color: '#fff',
    textAlign: 'left',
    cursor: 'pointer',
    fontSize: 14,
    padding: '6px 8px',
    borderRadius: 6,
  },
  format: { color: '#888', fontSize: 12 },
  delete: { background: 'none', border: 'none', color: '#ff6b6b', cursor: 'pointer', fontSize: 16 },
}
