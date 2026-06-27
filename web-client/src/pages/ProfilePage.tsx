/**
 * Account profile: shows the signed-in user, their win/loss record, and their saved decks (with a
 * deep link to open each in the deckbuilder). Prompts sign-in when anonymous.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import { useNavigate } from 'react-router-dom'
import {
  type AccountStats,
  type DeckSummary,
  deleteDeck as apiDeleteDeck,
  fetchStats,
  listDecks,
} from '@/api/account'
import { LoginModal } from '@/components/auth/LoginModal'
import { useAuthStore } from '@/store/authStore'

export function ProfilePage() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const status = useAuthStore((s) => s.status)
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const init = useAuthStore((s) => s.init)
  const logout = useAuthStore((s) => s.logout)

  const [stats, setStats] = useState<AccountStats | null>(null)
  const [decks, setDecks] = useState<DeckSummary[]>([])
  const [loginOpen, setLoginOpen] = useState(false)

  useEffect(() => {
    if (status === 'idle') void init()
  }, [status, init])

  useEffect(() => {
    if (status !== 'authenticated') return
    void fetchStats().then(setStats).catch(() => setStats(null))
    void listDecks().then(setDecks).catch(() => setDecks([]))
  }, [status])

  const removeDeck = async (id: number) => {
    await apiDeleteDeck(id)
    setDecks((prev) => prev.filter((d) => d.id !== id))
  }

  if (status === 'authenticated' && user) {
    return (
      <div style={styles.wrap}>
        <div style={styles.container}>
          <div style={styles.header}>
            <button type="button" style={styles.link} onClick={() => navigate('/')}>
              ← Home
            </button>
            <button type="button" style={styles.link} onClick={logout}>
              Sign out
            </button>
          </div>

          <h1 style={styles.title}>{user.displayName}</h1>
          <p style={styles.muted}>{user.email}</p>

          <div style={styles.statsRow}>
            <Stat label="Games" value={stats?.games ?? 0} />
            <Stat label="Wins" value={stats?.wins ?? 0} />
            <Stat label="Losses" value={stats?.losses ?? 0} />
            <Stat label="Win rate" value={stats ? `${Math.round(stats.winRate * 100)}%` : '—'} />
          </div>

          <h2 style={styles.section}>Saved decks</h2>
          {decks.length === 0 ? (
            <p style={styles.muted}>No saved decks yet. Build one and save it from the deckbuilder.</p>
          ) : (
            <ul style={styles.deckList}>
              {decks.map((deck) => (
                <li key={deck.id} style={styles.deckItem}>
                  <button
                    type="button"
                    style={styles.deckName}
                    onClick={() => navigate(`/deckbuilder?accountDeck=${deck.id}`)}
                  >
                    {deck.name}
                    {deck.format ? <span style={styles.format}> · {deck.format}</span> : null}
                  </button>
                  <button type="button" style={styles.deleteBtn} onClick={() => void removeDeck(deck.id)}>
                    Delete
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    )
  }

  const resolving = status === 'idle' || status === 'loading'

  return (
    <div style={styles.wrap}>
      <div style={styles.container}>
        <button type="button" style={styles.link} onClick={() => navigate('/')}>
          ← Home
        </button>
        <h1 style={styles.title}>Your account</h1>
        {accountsEnabled ? (
          <>
            <p style={styles.muted}>
              Sign in to save decks to the cloud and track your win/loss record.
            </p>
            <button type="button" style={styles.primary} onClick={() => setLoginOpen(true)}>
              Sign in
            </button>
            <LoginModal open={loginOpen} onClose={() => setLoginOpen(false)} />
          </>
        ) : resolving ? (
          <p style={styles.muted}>Loading…</p>
        ) : (
          <p style={styles.muted}>Accounts aren't available on this server.</p>
        )}
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div style={styles.stat}>
      <div style={styles.statValue}>{value}</div>
      <div style={styles.statLabel}>{label}</div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { minHeight: '100vh', backgroundColor: '#0a0a15', padding: '32px 16px' },
  container: { maxWidth: 720, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 12 },
  header: { display: 'flex', justifyContent: 'space-between' },
  link: { background: 'none', border: 'none', color: '#8b9bff', cursor: 'pointer', fontSize: 14, padding: 0 },
  title: { margin: '8px 0 0', color: '#fff', fontSize: 28 },
  muted: { margin: 0, color: '#888', fontSize: 14 },
  section: { margin: '20px 0 4px', color: '#fff', fontSize: 18 },
  statsRow: { display: 'flex', gap: 12, marginTop: 12, flexWrap: 'wrap' },
  stat: {
    flex: '1 1 120px',
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 12,
    padding: '16px 12px',
    textAlign: 'center',
  },
  statValue: { color: '#fff', fontSize: 26, fontWeight: 700 },
  statLabel: { color: '#888', fontSize: 12, marginTop: 4, textTransform: 'uppercase', letterSpacing: 0.5 },
  deckList: { listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 8 },
  deckItem: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 10,
    padding: '10px 14px',
  },
  deckName: { background: 'none', border: 'none', color: '#fff', cursor: 'pointer', fontSize: 15, textAlign: 'left' },
  format: { color: '#888', fontSize: 13 },
  deleteBtn: { background: 'none', border: 'none', color: '#ff6b6b', cursor: 'pointer', fontSize: 13 },
  primary: {
    alignSelf: 'flex-start',
    marginTop: 8,
    padding: '10px 18px',
    borderRadius: 8,
    border: 'none',
    backgroundColor: '#5b6ee1',
    color: '#fff',
    fontWeight: 600,
    cursor: 'pointer',
  },
}
