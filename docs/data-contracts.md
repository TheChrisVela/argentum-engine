# Data Interfaces & Contracts

This document defines the JSON payloads exchanged between `web-client` and `game-server`.

## 1. Core Philosophy

* **Server -> Client:** The Server pushes the **Truth**. The Client renders it.
* **Client -> Server:** The Client pushes **Intent**. The Server validates it.

## 2. Gameplay Payload (WebSocket)

### A. State Update (Server -> Client)

Sent whenever the game state changes.

```json
{
  "type": "stateUpdate",
  // 1. The Visual State (Masked)
  "state": {
    "activePlayerId": "player-1",
    "phase": "MAIN_1",
    "zones": [
      {
        "name": "BATTLEFIELD",
        "cards": [
          {
            "id": "ent-1",
            "name": "Grizzly Bears",
            "tapped": true,
            "pt": "2/2"
          }
        ]
      },
      {
        "name": "HAND",
        "ownerId": "player-1",
        "cards": [
          {
            "id": "ent-2",
            "name": "Generous Gift"
          },
          // Visible to owner
          {
            "id": "ent-3",
            "name": "???"
          }
          // Masked to opponent
        ]
      }
    ]
  },
  // 2. The Animation Stream (What just happened?)
  "events": [
    {
      "type": "Tapped",
      "entityId": "ent-1"
    },
    {
      "type": "DamageDealt",
      "targetId": "player-2",
      "amount": 2
    }
  ],
  // 3. The Legal Actions (What can I do now?)
  // The ENGINE calculates this. The Client just renders it.
  "legalActions": [
    {
      "actionId": "act-1",
      "type": "PlayLand",
      "description": "Play Forest",
      "sourceId": "ent-5"
    },
    {
      "actionId": "act-2",
      "type": "CastSpell",
      "description": "Cast Shock",
      "sourceId": "ent-6",
      // If targeting is needed, the engine provides the Valid Candidates
      "targeting": {
        "required": true,
        "validTargets": [
          "ent-1",
          "player-2",
          "player-1"
        ]
      }
    },
    {
      "actionId": "act-3",
      "type": "PassPriority",
      "description": "Pass Turn"
    }
  ]
}
```

### B. Action Submission (Client -> Server)

Sent when the user interacts with the UI.

**Simple Action (No targets):**

```json
{
  "type": "submitAction",
  "action": {
    "type": "PlayLand",
    "cardId": "ent-5"
  }
}
```

**Complex Action (With targets):**

```json
{
  "type": "submitAction",
  "action": {
    "type": "CastSpell",
    "cardId": "ent-6",
    "targets": [
      {
        "id": "ent-1",
        "type": "Creature"
      }
    ]
  }
}
```

---

## 3. Drafting Payload (REST / HTTP)

Drafting is lower frequency, so standard HTTP JSON is used.

**Request: Pick a Card**
`POST /api/draft/pick`

```json
{
  "draftSessionId": "sess_draft_0912",
  "packId": "pack_88",
  "cardId": "uuid-shivan-dragon"
}
```

**Response: Updated State**

```json
{
  "status": "PickRecorded",
  "waitingForOthers": true,
  // Or, if the next pack is ready:
  "nextPack": {
    "packId": "pack_89",
    "cards": [
      ...
    ]
  }
}

## 4. Scenario Builder Payload (REST / HTTP)

The Scenario Builder lets any player construct an arbitrary board state and play it. It is a
production feature: `POST /api/scenarios` is **not** gated behind `game.dev-endpoints.enabled`
(the older `POST /api/dev/scenarios` is the dev-only equivalent and shares the same request
shape + builder via `ScenarioBuilderService` / `ScenarioSessionFactory`).

**Request: `POST /api/scenarios`** (`ScenarioRequest`)

```json
{
  "player1Name": "Me",
  "player2Name": "Also me",
  "player1": {
    "lifeTotal": 20,
    "hand": ["Lightning Bolt"],
    "battlefield": [
      { "name": "Grizzly Bears", "tapped": true, "counters": { "PLUS_ONE_PLUS_ONE": 2 } },
      { "name": "Pacifism", "attachedTo": "Grizzly Bears" }
    ],
    "graveyard": ["Mountain"],
    "exile": ["Swamp"],
    "library": ["Forest", "Forest"],
    "commanders": []
  },
  "player2": { "lifeTotal": 20, "battlefield": [{ "name": "Hill Giant" }] },
  "phase": "PRECOMBAT_MAIN",
  "activePlayer": 1,
  "mode": "SELF"
}
```

- `mode` selects how the opponent seat is filled: `SELF` (single-client hotseat / play against
  yourself — one token controls both seats), `AI` (engine AI, requires `game.ai.enabled`;
  `aiPlayer` 1|2 picks the seat), or `TWO_PLAYER` (two tokens). When omitted it is derived from
  `aiPlayer` for back-compat.
- Validation rejects unknown card names and (production) enforces per-zone + total card caps,
  returning `400` with `{ "errors": ["Unknown card: …", …] }`.

**Response** (`ScenarioResponse`)

```json
{
  "sessionId": "…",
  "player1": { "name": "Me", "token": "<token>", "playerId": "player-1" },
  "player2": { "name": "Also me", "token": "<token>", "playerId": "player-2" },
  "message": "Hotseat scenario created — you control both players.",
  "mode": "SELF"
}
```

The client joins by navigating to `/?token=<token>` (token-based connect). For `SELF`/`AI` a
single human token is returned (in `SELF` both `playerX.token` echo the same value); for
`TWO_PLAYER` the two tokens differ.

### Hotseat (`hotseat` on the client state)

`SELF` mode stamps a `HotseatControlComponent(controllerId)` on every seat, so the engine's
`GameState.actorFor(playerId)` routes input authority (decision delivery, legal-action
enumeration, per-action seat authorization, and hand visibility) for both seats to the single
connection — the non-turn-scoped generalization of the Mindslaver-style hijack seam. The
client state carries a boolean **`hotseat`** so the UI can show a "controlling both players"
banner and act for whichever seat currently holds priority (board actions ride the
server-provided `legalActions`, which already carry the acting seat; `SubmitDecision` is stamped
with `pendingDecision.playerId`; combat declarations with the active/defending seat).

### "Share frame as scenario" (replay)

The replay viewer reproduces an **exact full-state snapshot** — stack, targets, floating effects,
mana, counters and all trackers, not just the public board. The full (unmasked) `GameState` is
recorded per frame (`GameReplayRecord.fullStates`; cheap because immutable frames structurally
share their component objects) and never surfaced during normal masked viewing. Two entry points:

- **Share as scenario** → copies a *short* link that only references the stored frame:
  `/scenario?replay=<gameId>&frame=<n>`. Opening it `POST`s to `/api/scenarios/from-replay-frame`
  (`{gameId, frame, mode?}`), which reads `GameReplayRecord.fullStates[frame]` and injects it into
  a fresh hotseat session (`mode=SELF` default). The link lives as long as the in-memory replay.
- **Download** → saves the frame's full state as a JSON file
  (`GET /api/public/replays/{gameId}/frames/{frame}/full-state`). Reload it locally from the
  builder's **Load file** button, which `POST`s the file to `/api/scenarios/from-state` (a raw
  serialized `GameState` body) and jumps in. "Load file" also accepts a **name-based** scenario
  JSON (like the `manual-scenarios/*.json`), loading it into the editable builder instead.

A snapshot is exact but **not editable** in the card-search builder; the builder's own name-based
`?s=` share remains for authoring/editing. The engine `GameState` is (de)serialized with
`persistenceJson` (`allowStructuredMapKeys` — `zones` is keyed by `ZoneKey`).
