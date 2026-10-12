# Architecture

```
            Minecraft (Fabric layer, not yet written)
   blocks · entities · screens · renderers · networking · commands
        │ calls in                         ▲ calls out
        ▼                                  │
  ┌────────────────────── PlaytimeEngine (one per world) ─────────────────────┐
  │ PlayerSessions   join/menu/new game/death/checkpoints/unlocks             │
  │ DeviceInteractions  place/use/insert/enter/doors/links                    │
  │ GrabPackService  fire/pull/release, cable ticks, hand abilities           │
  │ MonsterDirector  spawn limits, AI think(), catches, stuns, despawns       │
  │ ActionRunner     executes Actions, delayed queue (saved)                  │
  ├───────────────────────────────────────────────────────────────────────────┤
  │ MenuTriggerTracker │ ProgressionService + PlayerSaveStore │ ProfileRegistry│
  │ HandRegistry/HandService │ AccessEvaluator/DoorService                    │
  │ ConnectionGraph + SignalBus │ PowerNetwork │ PuzzleEngine + templates     │
  │ MonsterBrain │ ChaseController │ StoryEngine │ DialogueSession            │
  ├───────────────────────────────────────────────────────────────────────────┤
  │ ContentLoader/ContentPack (JSON) │ WorldConfig │ WorldState │ MapValidator │
  └───────────────────────────────────────────────────────────────────────────┘
                       GameBridge (interface the Fabric layer implements)
```

## Principles

* **Server-authoritative.** Clients send intent only: fire this launcher, pick option 2, press
  Continue. They never send targets, positions or results. Hand selection, menu actions, dialogue
  choices and editor saves are all validated on the server. `RateLimiter` throttles spammy requests.
* **Event-driven.** Doors close from a priority queue. Power recomputes only when something changed or a
  temporary charge expires (tracked as a single next-expiry tick). Signals travel along indexed links.
  Puzzles receive events through a device→puzzle index. Only active puzzles and chases tick. Nothing
  scans the world.
* **Fault-isolated.** Each content file loads on its own. Each action, puzzle event and chase tick is
  wrapped, so one broken definition logs an error instead of stopping the server. Signal chains, action
  chains, story-event chains, puzzle events and condition nesting all have depth limits.
* **Persistent and versioned.** Each JSON file has a `schemaVersion` and a `SaveMigrator`. Writes are
  atomic and keep a `.bak` copy. A corrupt player save is moved aside and replaced with a fresh one.
* **Per-world isolation.** Everything lives under `<world>/playtime/`. Built-in content comes from the
  jar and can be overridden per world.
* **Minecraft-free core.** The core has no Minecraft types, so it compiles and is tested with plain
  JUnit, and the game layer stays thin.

## Persistence map

| Data | File | Notes |
| --- | --- | --- |
| World settings | `world_config.json` | Written by World Settings and `updateConfig`. |
| Devices, links, device states, timers, doors, puzzles, world flags, fired events, world-completed chases, shared save | `world_state.json` | Saved on world save and autosaved every 60 s when dirty. |
| Player save slots, first-entry flag, assigned profile | `players/<uuid>.json` | Saved when dirty, on checkpoints and on disconnect. |
| Map definitions | `content/<kind>/*.json` | Edited by hand or by in-game editors (`saveDefinition`). |
| Active chases, monster entities, cable state, menu session | memory | Deliberately not saved. Encounters restart from their triggers. |

## Multiplayer rules

| Topic | Rule |
| --- | --- |
| Individual progression | Each player's save holds their unlocks. Doors, devices and puzzles are physical world state shared by everyone. A completed puzzle credits every participant. |
| Shared and host-owned progression | One save in `world_state.json`. Any player's progress counts. `resetAuthority` decides who may start a new game or reset encounters. A death never removes a hand the team earned. |
| Shared puzzles | Completion is idempotent: the first success completes the puzzle and later events are ignored, so rewards are paid once. |
| Monster targeting | `targetPolicy` chooses among visible players. A chase prefers the player who triggered it. |
| Chases | Players within `chaseParticipationRadius` join. A stage condition holds if any participant satisfies it. When a player is caught, either they alone fail (and respawn at the checkpoint) or the whole chase fails (`chaseFailsForAll`). |

## Extending

* **New hand.** Add a JSON file. A new kind of ability also needs a handler in `GrabPackService`; the
  validator warns about abilities that have no handler.
* **New puzzle type.** Implement `PuzzleLogic` (stateless; keep state in `PuzzleRun`) and register it
  in `PuzzleTemplates`.
* **New monster.** Add a JSON file with an existing `behaviour`. A new personality means adding a
  `BehaviourProfile` and a branch in `MonsterBrain.decide`.
* **New action or condition.** Add an enum value, a `case` in `ActionRunner` or `Condition`, and a
  validator check.
