import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import './styles/variables.css'
import './styles/responsive.css'
import 'mana-font/css/mana.min.css'
import 'keyrune/css/keyrune.min.css'
import App from './App'
import { TournamentEntryPage } from './components/tournament/TournamentEntryPage'
import { JoinLobbyPage } from './components/lobby/JoinLobbyPage'
import { AdminPage } from './components/admin/AdminPage'
import { ReplayPage } from './components/replay/ReplayPage'
import { DeckbuilderPage } from './components/deckbuilder/DeckbuilderPage'
import { ScenarioBuilderPage } from './components/scenario/ScenarioBuilderPage'
import { LlmTournamentPage } from './components/llmTournament/LlmTournamentPage'
import { SetCompletionPage } from './components/setCompletion/SetCompletionPage'
import { initAnalytics } from './utils/analytics'

initAnalytics()

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('Root element not found')
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/tournament/:lobbyId" element={<TournamentEntryPage />} />
        <Route path="/join/:lobbyId" element={<JoinLobbyPage />} />
        <Route path="/replay/:gameId" element={<ReplayPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/deckbuilder" element={<DeckbuilderPage />} />
        <Route path="/deckbuilder/:deckId" element={<DeckbuilderPage />} />
        <Route path="/scenario" element={<ScenarioBuilderPage />} />
        <Route path="/set-completion" element={<SetCompletionPage />} />
        <Route path="/llm-tournament" element={<LlmTournamentPage />} />
        <Route path="/llm-tournament/:id" element={<LlmTournamentPage />} />
        <Route path="*" element={<App />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>
)
