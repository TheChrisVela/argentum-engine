package com.wingedsheep.gameserver.ranking

import com.wingedsheep.gameserver.lobby.TournamentFormat
import com.wingedsheep.sdk.core.DeckFormat

/**
 * Derives the [RankedMode] a 1v1 game belongs to from its lobby's format. This is the single source of
 * truth for the LIMITED / CONSTRUCTED / COMMANDER split, computed once at game creation and carried on
 * the [GameSession][com.wingedsheep.gameserver.session.GameSession] so the game-over path doesn't have
 * to re-derive it (the lobby may already be gone by then for quick games).
 */
object Ranked {
    /**
     * Quick-game mode: a commander-shaped [DeckFormat] is COMMANDER; any other constructed restriction
     * (or Momir, which uses a fixed constructed-style pool) is CONSTRUCTED; no restriction means a
     * random sealed pool, i.e. LIMITED.
     */
    fun modeForQuickGame(format: DeckFormat?, momirBasic: Boolean): RankedMode = when {
        format?.isCommanderShape == true -> RankedMode.COMMANDER
        format != null || momirBasic -> RankedMode.CONSTRUCTED
        else -> RankedMode.LIMITED
    }

    /**
     * Tournament mode: commander draft/sealed and a commander-shaped premade [deckFormat] are COMMANDER;
     * other premade tournaments are CONSTRUCTED; pool-built tournaments (sealed/draft/winston/grid) are
     * LIMITED.
     */
    fun modeForTournament(format: TournamentFormat, deckFormat: DeckFormat?): RankedMode = when {
        format.isCommanderFormat -> RankedMode.COMMANDER
        format == TournamentFormat.PREMADE_DECKS ->
            if (deckFormat?.isCommanderShape == true) RankedMode.COMMANDER else RankedMode.CONSTRUCTED
        else -> RankedMode.LIMITED
    }
}
