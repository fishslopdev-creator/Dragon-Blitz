# Fabric integration plan

The engine core is finished and tested on its own. What remains is the Minecraft side: blocks, items,
entities, screens, rendering and networking. That code lives in `fabric/` and talks to the engine only
through `PlaytimeEngine` (calls in) and `GameBridge` (calls out).

**Why it isn't written yet.** This repository was built in an environment whose network policy blocked
`maven.fabricmc.net`, `meta.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`,
`libraries.minecraft.net` and the GeckoLib maven. Without those hosts, Loom cannot download Minecraft
or the mappings, so no Minecraft code could be compiled or tested. Code written blind against 1.21.11
would contain guessed method names, so none was written. Allow those hosts (or build locally), then
work through the stages below, compiling after each one.

## Versions to verify first

From Fabric's 1.21.11 announcement (December 2025): Loader **0.18.1**, Loom **1.14**. Fabric API builds
for 1.21.11 are named `0.141.x+1.21.11`. 1.21.11 is the last version with Yarn; Fabric recommends Mojang's
official mappings, which `fabric/build.gradle` uses. Before the first build, check the latest versions at
<https://fabricmc.net/develop> and in the 1.21.11 docs at <https://docs.fabricmc.net/1.21.11/develop/>, and
update `gradle.properties`.

For entity models and animations, check whether a GeckoLib release supports 1.21.11. If one does,
uncomment the dependency in `fabric/build.gradle`. The generated `.geo.json` and `.animation.json` files
are in the Bedrock format that GeckoLib and Blockbench use. If GeckoLib isn't available, render the same
geometry with vanilla `EntityModel` layers; the bone names in the geo files are what the animations
drive.

## Stage F1: bootstrap

* `PlaytimeMod implements ModInitializer`; add `entrypoints.main` to `fabric.mod.json`.
* `ServerLifecycleEvents.SERVER_STARTED`: open
  `PlaytimeEngine.open(server.getWorldPath(LevelResource.ROOT).resolve("playtime"), bridge)`.
  This gives one engine per world save folder, so configuration never leaks between worlds.
* `ServerLifecycleEvents.SERVER_STOPPING`: call `engine.save()`. Also save when the world itself saves.
* `ServerTickEvents.END_SERVER_TICK`: call `engine.tick(overworld.getGameTime())`.
* Implement `GameBridge` as `FabricGameBridge`. Players are looked up through `server.getPlayerList()`;
  `isOperator` uses the server's permission check for level 2.

## Stage F2: player lifecycle and menu

| Engine call | Fabric hook |
| --- | --- |
| `sessions().onJoin(uuid, mode)` | `ServerPlayConnectionEvents.JOIN` |
| `sessions().onLeave(uuid)` | `ServerPlayConnectionEvents.DISCONNECT` |
| `sessions().onRespawn(uuid, mode, !alive)` | `ServerPlayerEvents.AFTER_RESPAWN` (`alive == false` means death) |
| `sessions().onDimensionChange(uuid, mode)` | `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD` |
| `sessions().onGameModeChange(uuid, from, to)` | Mixin at the return of `ServerPlayer#setGameMode` (no Fabric event exists; keep the mixin isolated) |
| `sessions().onDeath(uuid)` | `ServerLivingEntityEvents.AFTER_DEATH`. Use `keepInventory` from the result and drop or retain items. |
| `sessions().respawnPoint(uuid)` | Set the respawn position before respawn, or teleport in `AFTER_RESPAWN`. |

`openMainMenu` sends a `MenuPayload` (as JSON) in a custom S2C payload
(`CustomPacketPayload` registered with `PayloadTypeRegistry.playS2C()`). The client opens a
`PlaytimeMenuScreen extends Screen` that draws the background, logo or title, slot list and ordered
buttons, plays the menu music, and sends C2S payloads (`ClientRequests.MenuContinue`, `MenuNewGame`,
`MenuConfirmNewGame`, `MenuClosed`). The server handles these in `ServerPlayNetworking` receivers on the
server thread.

## Stage F3: blocks, items and the Connection Device

* Register 19 blocks (ids in `assets/playtime/blockstates/`) with block entities that store their
  engine device id. On placement call `interactions().place(type, pos, null, defaults)`; on break call
  `interactions().breakAt(pos)`.
* `use` → `interactions().use(player, deviceId, heldItemId)`. Stepping into volumes → `interactions().enter`.
  Scan trigger-volume bounding boxes from the block entity tick, throttled to every 4–10 ticks.
* Device GUIs are operator-only. They open from the server, and the server re-checks operator status
  when it receives the save payload (`interactions().configure`).
* Connection Device item: first use selects the source, second the destination, then a screen picks the
  type, delay, conditions and target. Submitting calls `interactions().connect`, which returns the
  validation issues to show. Use `/playtime connections` data to draw link previews (particle lines
  while the item is held).
* Items: `power_core`, `fuse`, keycards and keys. Their ids must match the `credentials/` and socket
  ids. `heldCredentials` scans the inventory for items whose id is a credential.
* `lights` actions: toggle light blocks inside the box, capped per tick.

## Stage F4: GrabPack

* Item or equipment for the GrabPack; `progress.grabPack` decides which model is shown.
* Key bindings (`KeyBindingHelper`): fire left, fire right, pull, release, hand wheel, journal, in a
  "GrabPack" category so they don't conflict with movement.
* C2S `FireHand(side)`: on the server, rate-limit with `RateLimiter`, ray-cast from the player's eye
  (block and entity clip, range = hand range), turn the hit into a `TargetInfo`, then call
  `grabPack().fire(...)`.
* Each tick, for players with active cables, call `grabPack().tick(uuid, leftOrigin, rightOrigin,
  cableWorld)`. `CableWorld` uses `level.hasChunkAt`, a block clip for line of sight, and entity lookup
  by UUID. Apply the returned `HandEffect`s server-side:
  * pull: entity velocity;
  * self-pull and launch: player delta movement plus a motion packet;
  * light: a temporary light block or light entity;
  * pressure blast and burn: only on blocks with the `#playtime:pressure_breakable` and
    `#playtime:flammable` tags.
* Send `CableSnapshot`s to tracking clients. The client renders the cable as a line strip or textured
  quad from the launcher to the hand. The hand uses the `geo/hands/*` model with first-person and
  third-person transforms. Keep the rendering code in a separate `client/render` package and any mixins
  (first-person arm or launcher placement) in their own package.
* Hand wheel: `HandWheelScreen` built from `hands().wheel(uuid, side)`. Mouse angle, scroll and number
  keys pick a hand; right-click cancels; `WheelMode.PAUSE` pauses only in single player. Selecting sends
  `SelectHand`, which the server checks with `hands().select`.

## Stage F5: monsters

* One `PathfinderMob` subclass per behaviour family, or a single `PlaytimeMonster` entity configured
  from its `MonsterDefinition`. Set its size from `width` and `height`. The entity should not be saved
  to disk, because the director recreates encounters.
* Each tick, call `director().think(entityUuid, senses)`, where `senses` reads the position, facing,
  nearby players (excluding creative and spectator) and clip-based line of sight. If `think` returns
  null, the entity is a leftover and should discard itself. Apply the intent:
  * `moveTarget` and `speed`: the navigation's `moveTo`, with a bounded search range;
  * `lookAt`: look control;
  * `animation`: GeckoLib controller;
  * `attackTarget`: swing, then `director().onAttack`;
  * `teleportTo`: `teleportTo`;
  * `despawn`: `discard()`.
* Footsteps, GrabPack fire and alarms call `director().hear(new Noise(...))`.

## Stage F6: story UI

* Title cards and status lines can use vanilla title/actionbar packets at first. Objective tracker HUD:
  `HudRenderCallback` (or the newer HUD element API, if the 1.21.11 docs prefer it).
* Dialogue screen: show `DialogueSession.View`; clicking an option sends `DialogueChoice(index)`.
* Journal screen: collectibles, objectives and recordings from a progress sync payload.
* Recordings: play the sound event and show the transcript.
* Credits screen: a scrolling list built from the chapter or event text.

## Stage F7: editors and commands

* Register `/playtime` with `CommandRegistrationCallback`. Every branch calls `AdminService`, which
  re-checks permission itself. Use `.requires(source -> source.hasPermission(2))` for privileged
  branches, except `menu` for oneself.
* World Settings screen (operator only), with 13 tabs: General, Main Menu, Player Setup, GrabPack,
  Hands, Keys and Doors, Puzzles, Monsters, Chases, Story Progression, Sound and Atmosphere,
  Multiplayer, Save Management (their labels are already in `lang/en_us.json`). It edits a copy of
  `WorldConfig` and sends it back; the server re-checks operator status and calls `updateConfig`.
* Player Setup Console: a profile list plus an editor. The inventory editor copies stacks from a
  creative-style grid into `ItemSpec`s, with item component data serialized as command-syntax
  components. Save through `saveDefinition(PROFILES, json)`.
* Puzzle, chase and monster editors: start with JSON editing in-game (a multiline text field validated
  by `saveDefinition`), then add form screens.

## Stage F8: testing in Minecraft

Run these checks with `./gradlew :fabric:runClient` and `:fabric:runServer`:

1. Join a configured world in Survival: the menu opens once. Switch Creative → Survival: it opens once
   (if enabled). Then die, respawn, change dimension and restart the server, and confirm the menu follows
   the world rules.
2. Hands: a locked hand can't be selected; a pickup unlocks it; the wheel stays in sync after relog and
   for two players at once.
3. Doors and links: card readers, scanners and powered doors all work, and links survive a restart.
4. `/playtime sample install` followed by a full playthrough of the Assembly Line and Factory Shutdown.
5. Multiplayer: individual vs shared saves, chase target policy, and a shared puzzle completing only once.
6. Performance: idle world tick time with many devices; a chase with a monster that gets stuck.
