/**
 * Admin dashboard: global, cross-user stats served from `/api/stats/admin/*`. Shows headline totals,
 * a games-per-day chart, mode/color distributions, the most-played cards and per-card win rates,
 * recorded tournaments, and an IP-based geolocation estimate of where players connect from.
 *
 * All data is read-only and gated behind the same admin password as the replay browser. Raw IPs are
 * never returned here — the server resolves them to coarse locations server-side.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  type CardStat,
  type CardWinRate,
  type DailyCount,
  type GeoBucket,
  type GlobalOverview,
  type TournamentSummary,
  fetchCardWinRates,
  fetchColorDistribution,
  fetchGamesPerDay,
  fetchGeo,
  fetchModeDistribution,
  fetchOverview,
  fetchTopCards,
  fetchTournaments,
} from '@/api/adminStats'
import type { StatBucket } from '@/api/account'
import { colorLabel } from './statFormat'

export function AdminDashboard({ password, onBack }: { password: string; onBack: () => void }) {
  const [overview, setOverview] = useState<GlobalOverview | null>(null)
  const [perDay, setPerDay] = useState<DailyCount[]>([])
  const [modes, setModes] = useState<StatBucket[]>([])
  const [colors, setColors] = useState<StatBucket[]>([])
  const [cards, setCards] = useState<CardStat[]>([])
  const [winRates, setWinRates] = useState<CardWinRate[]>([])
  const [tournaments, setTournaments] = useState<TournamentSummary[]>([])
  const [geo, setGeo] = useState<GeoBucket[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [ov, pd, md, cl, cs, wr, tn] = await Promise.all([
          fetchOverview(password),
          fetchGamesPerDay(password, 30),
          fetchModeDistribution(password),
          fetchColorDistribution(password),
          fetchTopCards(password, 25),
          fetchCardWinRates(password, 5, 25),
          fetchTournaments(password, 25),
        ])
        if (cancelled) return
        setOverview(ov)
        setPerDay(pd)
        setModes(md)
        setColors(cl)
        setCards(cs)
        setWinRates(wr)
        setTournaments(tn)
        // Geo can be slow (external lookups) — load it separately so the rest renders first.
        fetchGeo(password).then((g) => !cancelled && setGeo(g)).catch(() => {})
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load dashboard')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [password])

  return (
    <div style={s.page}>
      <div style={s.container}>
        <div style={s.headerRow}>
          <h1 style={s.title}>Dashboard</h1>
          <button type="button" style={s.link} onClick={onBack}>
            ← Replays
          </button>
        </div>

        {error && <p style={s.error}>{error}</p>}

        <div style={s.cardRow}>
          <Metric label="Games" value={overview?.totalGames} />
          <Metric label="Players" value={overview?.totalPlayers} />
          <Metric label="Accounts" value={overview?.totalAccounts} />
          <Metric label="Tournaments" value={overview?.totalTournaments} />
          <Metric label="Games (24h)" value={overview?.gamesLast24h} />
          <Metric label="Games (7d)" value={overview?.gamesLast7d} />
        </div>

        <Panel title="Games per day (last 30 days)">
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={perDay} margin={{ top: 8, right: 16, bottom: 0, left: -16 }}>
              <CartesianGrid stroke="#22223a" strokeDasharray="3 3" />
              <XAxis dataKey="day" stroke="#666" fontSize={11} tickFormatter={(d: string) => d.slice(5)} />
              <YAxis stroke="#666" fontSize={11} allowDecimals={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="count" stroke="#5b6ee1" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </Panel>

        <div style={s.twoCol}>
          <Panel title="Game modes">
            <BucketBars data={modes} fill="#5b9ee1" />
          </Panel>
          <Panel title="Colors played">
            <BucketBars data={colors.map((c) => ({ label: colorLabel(c.label), count: c.count }))} fill="#e1a35b" />
          </Panel>
        </div>

        <div style={s.twoCol}>
          <Panel title="Most-played cards">
            <Table head={['Card', 'Copies', 'Decks']}>
              {cards.map((c) => (
                <tr key={c.cardName}>
                  <td style={s.td}>{c.cardName}</td>
                  <td style={s.tdNum}>{c.copies}</td>
                  <td style={s.tdNum}>{c.decks}</td>
                </tr>
              ))}
            </Table>
          </Panel>
          <Panel title="Highest win-rate cards (≥5 decks)">
            <Table head={['Card', 'Win %', 'Decks']}>
              {winRates.map((c) => (
                <tr key={c.cardName}>
                  <td style={s.td}>{c.cardName}</td>
                  <td style={s.tdNum}>{Math.round(c.winRate * 100)}%</td>
                  <td style={s.tdNum}>{c.decks}</td>
                </tr>
              ))}
            </Table>
          </Panel>
        </div>

        <Panel title="Tournaments">
          {tournaments.length === 0 ? (
            <p style={s.muted}>No tournaments recorded yet.</p>
          ) : (
            <Table head={['Date', 'Name', 'Mode', 'Players', 'Winner']}>
              {tournaments.map((t, i) => (
                <tr key={`${t.endedAt}-${i}`}>
                  <td style={s.td}>{t.endedAt.slice(0, 10)}</td>
                  <td style={s.td}>{t.name ?? '—'}</td>
                  <td style={s.td}>{t.gameMode ?? '—'}</td>
                  <td style={s.tdNum}>{t.playerCount}</td>
                  <td style={s.td}>{t.winnerName ?? '—'}</td>
                </tr>
              ))}
            </Table>
          )}
        </Panel>

        <Panel title="Where players connect from">
          {geo.length === 0 ? (
            <p style={s.muted}>Resolving locations…</p>
          ) : (
            <Table head={['Country', 'Region', 'City', 'Games']}>
              {geo.map((g, i) => (
                <tr key={`${g.countryCode}-${g.city}-${i}`}>
                  <td style={s.td}>{g.country ?? 'Unknown'}</td>
                  <td style={s.td}>{g.region ?? '—'}</td>
                  <td style={s.td}>{g.city ?? '—'}</td>
                  <td style={s.tdNum}>{g.games}</td>
                </tr>
              ))}
            </Table>
          )}
        </Panel>
      </div>
    </div>
  )
}

function Metric({ label, value }: { label: string; value?: number | undefined }) {
  return (
    <div style={s.metric}>
      <div style={s.metricValue}>{value ?? '—'}</div>
      <div style={s.metricLabel}>{label}</div>
    </div>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={s.panel}>
      <h2 style={s.panelTitle}>{title}</h2>
      {children}
    </div>
  )
}

function BucketBars({ data, fill }: { data: StatBucket[]; fill: string }) {
  if (data.length === 0) return <p style={s.muted}>No data yet.</p>
  return (
    <ResponsiveContainer width="100%" height={Math.max(120, data.length * 28)}>
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 4, left: 8 }}>
        <XAxis type="number" stroke="#666" fontSize={11} allowDecimals={false} />
        <YAxis type="category" dataKey="label" stroke="#999" fontSize={11} width={90} />
        <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#ffffff10' }} />
        <Bar dataKey="count" fill={fill} radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}

function Table({ head, children }: { head: string[]; children: React.ReactNode }) {
  return (
    <div style={s.tableWrap}>
      <table style={s.table}>
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={h} style={i === 0 ? s.th : s.thNum}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

const tooltipStyle: React.CSSProperties = {
  backgroundColor: '#12121e',
  border: '1px solid #2a2a3e',
  borderRadius: 6,
  color: '#ddd',
  fontSize: 12,
}

const s: Record<string, React.CSSProperties> = {
  page: { minHeight: '100vh', backgroundColor: '#0a0a12', color: '#ccc', padding: '24px 16px' },
  container: { maxWidth: 960, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 16 },
  headerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  title: { margin: 0, color: '#fff', fontSize: 26 },
  link: { background: 'none', border: 'none', color: '#8b9bff', cursor: 'pointer', fontSize: 14 },
  error: { color: '#ef4444', fontSize: 13, margin: 0 },
  cardRow: { display: 'flex', gap: 12, flexWrap: 'wrap' },
  metric: {
    flex: '1 1 120px',
    backgroundColor: '#12121e',
    border: '1px solid #1f1f33',
    borderRadius: 12,
    padding: '14px 12px',
    textAlign: 'center',
  },
  metricValue: { color: '#fff', fontSize: 24, fontWeight: 700 },
  metricLabel: { color: '#777', fontSize: 11, marginTop: 4, textTransform: 'uppercase', letterSpacing: 0.5 },
  panel: { backgroundColor: '#12121e', border: '1px solid #1f1f33', borderRadius: 12, padding: 16 },
  panelTitle: { margin: '0 0 12px', color: '#e0e0e0', fontSize: 16 },
  twoCol: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 },
  muted: { color: '#777', fontSize: 13, margin: 0 },
  tableWrap: { overflowX: 'auto' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: 13 },
  th: { textAlign: 'left', color: '#888', fontWeight: 600, padding: '6px 8px', borderBottom: '1px solid #2a2a3e' },
  thNum: { textAlign: 'right', color: '#888', fontWeight: 600, padding: '6px 8px', borderBottom: '1px solid #2a2a3e' },
  td: { textAlign: 'left', color: '#ccc', padding: '6px 8px', borderBottom: '1px solid #1a1a2a' },
  tdNum: { textAlign: 'right', color: '#ccc', padding: '6px 8px', borderBottom: '1px solid #1a1a2a' },
}
