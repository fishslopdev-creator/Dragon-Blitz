# Playtime Engine

A toy-factory horror game engine for **Minecraft Java Edition 1.21.11 (Fabric)**, inspired by the
gameplay structure of Poppy Playtime. It is built as an *engine*: every world is its own game, and map
makers set up hands, doors, puzzles, monsters, chases and story in JSON files or in-game editors, without
touching Java.

> **Project status (read this first).** This repository currently contains the **engine core** and the
> **assets and build setup for the Fabric layer**. The Fabric game layer (blocks, screens, renderers,
> networking, the Minecraft side of `GameBridge`) is **not written yet**. The build environment this was
> developed in could not reach `maven.fabricmc.net` or Mojang's servers, so Minecraft could not be
> downloaded, compiled against or launched. Nothing in this repository has been tested inside Minecraft.
> See [docs/FABRIC_INTEGRATION.md](docs/FABRIC_INTEGRATION.md) for exactly what remains and how each
> engine hook maps onto Fabric.

## What works today

The engine core (`engine-core/`) is plain Java 21 with Gson as its only dependency. It builds and its
106 tests pass. It implements:

| System | Highlights |
| --- | --- |
| Per-world configuration | Title, subtitle, chapter, main-menu layout/music/background, rules, multiplayer mode, monster toggles. Stored under `<world>/playtime/`, so worlds never share settings. |
| Main-menu policy | Shows the menu on login, first entry, after death and/or on `/gamemode survival`, per world. Ignores dimension changes and non-death respawns, de-duplicates rapid triggers, and has an admin bypass. |
| Saves | Per-player save slots, a shared save for co-op or host-owned worlds, New Game with explicit confirmation, death rules, versioned schemas with migrations, atomic writes with `.bak` fallback, quarantine of corrupt files. |
| Player Setup profiles | Starting GrabPack, hands, inventory, keys, chapter, checkpoint, sprint/crouch, death overrides, tutorials, interaction denial. Profiles can be assigned per player, team, spawn area or scenario flag. |
| Hands | Data-driven hand definitions with abilities, target kinds, interaction tags, range, cooldown, charges, upgrades and launcher/GrabPack compatibility. Includes nine hands: Blue, Red, Green, Purple, Flare, Omni-Hand, Pressure, Conductive and the original **Pulse Hand**. |
| GrabPack | Server-side cable simulation (fire, attach, pull, release, retract, cooldown) that snaps safely on range, obstruction, unloaded chunks, vanished targets and timeouts. Ability resolution covers scanners, power conduction and stored charge, jump pads, flares (light, stun, burn), pressure blasts, pulses and bounded self-pull. |
| Hand unlocks and wheel | Pickups (permanent, reusable, chapter-limited, shared), unlock conditions, notifications, tutorials, wheel data, and server-validated selection. |
| Doors and access | 14 door types; keys, keycards and clearance levels; hand-scanner and multi-hand rules; power and condition requirements; reasons shown when access is denied; timed, force-locked and permanently unlocked doors. |
| Connection graph | Typed links between devices with delays, conditions, inversion and AND-inputs. Instant loops are refused when a link is made, and a depth limit plus a per-tick budget contain delayed loops. |
| Power | Circuit solver with switches, fuse sockets, nodes that need activating, capacity/priority brownouts, overload trips and temporary charges. It is event-driven, with no per-tick scanning. |
| Puzzles | A common `PuzzleLogic` interface with templates A–J: power routing, fuse collection, keycard, colour sequence, timed switches, traversal, machine assembly, hand scanner, multi-room, and **staged** (chains other templates), plus pulse receivers. Completion is idempotent, and reset, attempts, rewards and persistence are configurable. |
| Monsters | Data-driven definitions and a `MonsterBrain` with 10 behaviour profiles (stalker, crawler, stealth, ambusher, hunter, sound-hunter, swarm, boss, NPC, scripted). Includes vision cones, line of sight, hearing, memory, search, sprint bursts, attack cooldowns, stuns, target policies and stuck recovery. A 21-entry roster ships (Chapters 1–5 characters and optional NPCs), plus the original **Playwright**. |
| Chases | Configurable chase controller: triggers, start delay, stages with timers, branches, per-stage routes, checkpoints, failure outcomes (respawn, restart, reset puzzles, return to menu), and a safety timeout. |
| Story | Events with triggers, conditions and 41 action types; dialogue trees with server-validated options; collectibles/journal; objectives; chapters; checkpoints. |
| Tools | Map validator (dangling ids, broken links, unreachable stages, self-triggering events...), `/playtime` command logic with permission checks, rate limiting for client requests, and backups. |
| Sample game | The Assembly Line puzzle, the Factory Shutdown chase, the Pulse Hand pickup and The Playwright, built from ordinary map content. A test plays it end to end, including a server restart mid-puzzle and a failed chase that is then retried. |

The `fabric/` module contains original assets: Blockbench-compatible geometry and animations for
The Playwright, the GrabPack 2.0 and all nine hands, textures, GUI art, block and item models, language
strings, and 27 synthesized sound effects. It also contains Fabric metadata and a Loom build file. It
has no Java code yet.

## Repository layout

```
engine-core/            Pure-Java engine (builds and tests anywhere with Java 21)
  src/main/java/dev/playtime/engine/
    config/ menu/ progress/ profile/        world config, menu policy, saves, setup profiles
    hand/ grabpack/                         hand registry, wheel, cable simulation
    access/ graph/ power/                   doors and keys, connection graph, power solver
    puzzle/ puzzle/templates/               puzzle engine and templates A–J
    monster/ chase/ story/ logic/           AI, chases, story events, conditions and actions
    content/ validate/ admin/ net/          JSON loading, validator, commands, client requests
    engine/                                 PlaytimeEngine facade and the GameBridge interface
    sample/                                 installer for the sample game
  src/main/resources/playtime/builtin/      built-in hands, packs, cards, profiles, monsters
  src/main/resources/playtime/sample/       sample game content (relative coordinates)
fabric/                 Fabric game layer: assets, metadata and build (Java not yet written)
docs/                   Map-making reference, architecture, Fabric integration plan
tools/                  Asset generator and content index updater
```

## Building

Requirements: JDK 21.

```bash
./gradlew :engine-core:build        # compiles, runs the 106 tests, produces the engine jar
```

The engine jar is written to `engine-core/build/libs/playtime-engine-core-0.1.0.jar`.

The Fabric module is switched off by default because it needs Fabric's maven and Mojang's servers.
On a machine with normal internet access:

```bash
./gradlew :fabric:build -PwithFabric=true
```

This step has **not** been run. Check the versions in `gradle.properties` against
<https://fabricmc.net/develop> first. Even if it succeeds, the result will not be a playable mod until
the game layer is written (see the integration plan).

## Installation (once the Fabric layer exists)

1. Install Fabric Loader 0.18.1 or newer for Minecraft 1.21.11, and Fabric API.
2. Put the Playtime Engine jar in `.minecraft/mods`.
3. Open a world. The engine creates `<world>/playtime/` with `world_config.json`, `world_state.json`,
   `players/` and `content/`.
4. As an operator, run `/playtime sample install` where you want the sample facility, or start building
   your own map with the Creative-tab tools.

## Making a map

Read [docs/MAP_MAKING.md](docs/MAP_MAKING.md). In short:

* World settings live in `<world>/playtime/world_config.json` (example:
  [docs/examples/world_config.json](docs/examples/world_config.json)).
* Every definition is one JSON file in `<world>/playtime/content/<kind>/`: `hands`, `doors`, `puzzles`,
  `monsters`, `routes`, `chases`, `events`, `dialogues`, `collectibles`, `objectives`, `chapters`,
  `checkpoints`, `profiles`, `profile_assignments`, `pickups`, `credentials` and `grabpacks`. A world file
  with the same id overrides a built-in one.
* Engine blocks (generators, readers, scanners, triggers...) are placed in the world and linked with the
  Connection Device.
* Run `/playtime validate` to list broken references, missing devices and suspicious settings.

The sample game in `engine-core/src/main/resources/playtime/sample/` is a worked example. Every part of
it uses the same files and blocks that map makers use.

## Content and originality

All models, textures, animations and sounds in this repository were generated for this project by
`tools/generate_sample_assets.py`. Nothing is extracted from Poppy Playtime or any other game. Character
entries in the monster roster are configurable behaviour definitions; only The Playwright, the Pulse
Hand, the GrabPack 2.0 and the hands have models so far. Poppy Playtime is a trademark of Mob
Entertainment. This is an unofficial fan engine and is not affiliated with Mob Entertainment.

## License

No license has been chosen yet. Add a `LICENSE` file before distributing the mod.
