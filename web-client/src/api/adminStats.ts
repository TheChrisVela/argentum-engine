/**
 * REST client for the admin dashboard's global stats (`/api/stats/admin/*`). Auth reuses the same
 * `X-Admin-Password` header as the replay browser; the password is held by the caller (AdminPage keeps
 * it in sessionStorage). These endpoints are only mounted when the server has accounts enabled.
 */
import type { StatBucket } from './account'

export interface GlobalOverview {
  readonly totalGames: number
  readonly totalPlayers: number
  readonly totalAccounts: number
  readonly totalTournaments: number
  readonly gamesLast24h: number
  readonly gamesLast7d: number
}

export interface DailyCount {
  readonly day: string
  readonly count: number
}

export interface GeoBucket {
  readonly country: string | null
  readonly countryCode: string | null
  readonly region: string | null
  readonly city: string | null
  readonly games: number
}

export interface CardStat {
  readonly cardName: string
  readonly copies: number
  readonly decks: number
}

export interface CardWinRate {
  readonly cardName: string
  readonly decks: number
  readonly wins: number
  readonly winRate: number
}

export interface TournamentSummary {
  readonly endedAt: string
  readonly name: string | null
  readonly format: string | null
  readonly gameMode: string | null
  readonly playerCount: number
  readonly winnerName: string | null
}

async function getAdminStats<T>(password: string, path: string): Promise<T> {
  const res = await fetch(`/api/stats/admin${path}`, { headers: { 'X-Admin-Password': password } })
  if (!res.ok) throw new Error(`Failed to load stats (${res.status})`)
  return (await res.json()) as T
}

export const fetchOverview = (pwd: string) => getAdminStats<GlobalOverview>(pwd, '/overview')
export const fetchGamesPerDay = (pwd: string, days = 30) =>
  getAdminStats<DailyCount[]>(pwd, `/games-per-day?days=${days}`)
export const fetchModeDistribution = (pwd: string) => getAdminStats<StatBucket[]>(pwd, '/modes')
export const fetchColorDistribution = (pwd: string) => getAdminStats<StatBucket[]>(pwd, '/colors')
export const fetchGeo = (pwd: string) => getAdminStats<GeoBucket[]>(pwd, '/geo')
export const fetchTopCards = (pwd: string, limit = 50) =>
  getAdminStats<CardStat[]>(pwd, `/cards?limit=${limit}`)
export const fetchCardWinRates = (pwd: string, minDecks = 10, limit = 50) =>
  getAdminStats<CardWinRate[]>(pwd, `/cards/win-rates?minDecks=${minDecks}&limit=${limit}`)
export const fetchTournaments = (pwd: string, limit = 50) =>
  getAdminStats<TournamentSummary[]>(pwd, `/tournaments?limit=${limit}`)
