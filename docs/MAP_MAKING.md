# Map-making reference

This is the reference for everything a map maker configures. All of it is data: JSON files, world
settings, and the settings stored on placed engine blocks. No Java is needed.

> The in-game editors (World Settings, Player Setup Console, Connection Device and the device GUIs) are
> part of the Fabric layer, which is not written yet. They will read and write the same JSON described
> here, so maps built by hand now will keep working.

## 1. Where things live

```
<world>/playtime/
  world_config.json      World Settings (title, menu, rules, multiplayer...)
  world_state.json       Runtime state: placed devices, links, doors, puzzles, shared save
  players/<uuid>.json    Per-player save slots
  content/<kind>/*.json  Map definitions, one per file
  backups/<timestamp>/   Created by /playtime backup
```

* Identifiers look like `namespace:path`. A bare `path` means `playtime:path`. Use lower case, digits,
  `_ - . /`.
* A content file without an `id` gets one from its path: `content/doors/vault.json` is
  `playtime:vault`, and `content/doors/lab/east.json` is `playtime:lab/east`.
* World files override built-in definitions that have the same id. For example, put
  `content/hands/blue_hand.json` in your world to change the Blue Hand there only.
* Enum values are lower case (`"every_login"`, `"keycard"`). Unknown values are reported by name.
* Colours are `"#RRGGBB"` or `"#AARRGGBB"` strings.
* A broken file is reported and skipped. The rest of the map still loads.

## 2. World settings: `world_config.json`

See [examples/world_config.json](examples/world_config.json) for a complete file.

| Key | Meaning |
| --- | --- |
| `enabled` | `false` leaves this world as plain Minecraft. |
| `gameTitle`, `subtitle`, `chapterName` | Shown on the main menu and title cards. |
| `startingChapter`, `defaultProfile` | Chapter and Player Setup profile for new games. |
| `saveSlots` | 1–10 slots per player. Ignored in shared modes. |
| `monsterToggles` | `{ "playtime:mini_huggies": false }` disables a monster in this world. |
| `mainMenu.enabled` | Turns the custom menu on or off. |
| `mainMenu.title`, `mainMenu.subtitle` | Override the world title and subtitle on the menu. |
| `mainMenu.background`, `mainMenu.logo` | Textures (resource-pack paths). |
| `mainMenu.music`, `mainMenu.musicVolume` | Sound event looped on the menu. |
| `mainMenu.showOn` | Any of `every_login`, `first_entry`, `after_death`, `survival_switch`. |
| `mainMenu.includeAdventureMode` | Treat Adventure like Survival for the menu. |
| `mainMenu.buttons` | List of `{type, label, visible, order}`. `type` is `continue`, `new_game`, `chapters`, `settings`, `multiplayer_info`, `journal` or `quit_to_title`. |
| `mainMenu.accentColor`, `textColor`, `animated`, `showProgressStatus`, `multiplayerInfo` | Appearance. |
| `rules.keepItemsOnDeath`, `rules.keepHandsOnDeath` | `keepHandsOnDeath: false` removes hands unlocked since the last checkpoint. |
| `rules.keepUncheckpointedKeysOnDeath` | Same rule for key items. |
| `rules.resetPowerOnDeath` | Switches generators and switches off when a player dies (opt out per device with `reset_on_death=false`). |
| `rules.resetActivePuzzlesOnDeath` | Resets puzzles the player was working on. |
| `rules.allowSelfPull`, `rules.maxSelfPullSpeed` | Grapple-style self-pull for hands with `self_pull`. |
| `rules.wheelMode` | `pause` or `live` hand wheel. |
| `rules.allowConsoleSelfService` | Lets non-operators use the Player Setup Console on themselves. Leave this off for real maps. |
| `rules.allowMenuCommand` | Lets players run `/playtime menu`. |
| `rules.tutorialsEnabled`, `rules.monsterDifficulty` | Tutorials on/off; 0.1–5 multiplier for monster speed and senses. |
| `multiplayer.progressionMode` | `individual`, `shared` (co-op) or `host_owned`. |
| `multiplayer.hostPlayer` | UUID. In `host_owned` mode the first player to join becomes host if this is unset. |
| `multiplayer.targetPolicy` | `nearest`, `triggering_player`, `lowest_health`, `random` or `host`. |
| `multiplayer.resetAuthority` | `operators_only`, `host_and_operators` or `any_participant`. Controls who may start a new game or reset shared encounters. |
| `multiplayer.chaseFailsForAll`, `chaseParticipationRadius` | Chase rules for groups. |
| `atmosphere.*` | Ambient loop, chase music, particle budget per tick. |

### When does the menu appear?

| Event | Shown when |
| --- | --- |
| Join in Survival | `every_login` is set, or `first_entry` is set and this is the player's first visit. |
| Respawn after death | `after_death` is set. Respawns from leaving the End never count. |
| `/gamemode survival` | `survival_switch` is set and the player was not already in Survival. |
| Dimension change | Never. |
| Within 2 seconds of the menu last being shown, or while it is open | Never (prevents duplicates). |
| Admin bypass (`/playtime menu bypass`) | Never, until the bypass is turned off. |

Menu actions are checked on the server. Continue and New Game only work while the server has the
menu open for that player. New Game over a save that has progress returns a confirmation that expires
after 60 seconds.

## 3. Engine blocks (devices)

Each engine block registers a **device** with an id, a type and a position. Its settings are text
key/values edited in its GUI. Every device also has `inputsRequired` (an AND of that many active
inputs) and `latch` (stays on once activated).

| Device type | Settings | Behaviour |
| --- | --- | --- |
| `power_switch`, `lever` | — | Toggles on use. A switch blocks power while off. |
| `button` | — | Sends a pulse. |
| `timed_switch` | `duration` (ticks, default 100) | Turns on, then off again after `duration`. |
| `generator` | `capacity` (10), `sockets`, `trip_on_overload`, `reset_on_death` | Supplies power while on and fully socketed. Use it with a part to insert that part. |
| `fuse_box`, `part_socket`, `machine` | `sockets` (`slot=item,slot=item`), `demand` | Using it with a matching item fills a slot and consumes the item. |
| `power_node` | `needs_activation` | With `needs_activation=true`, blocks power until activated (for example by a conducting hand). |
| `power_receiver`, `circuit_endpoint`, `light`, `alarm`, `door_controller`, `hand_scanner`, terminals... | `demand` (1), `priority` (0) | Consumers. When supply is short, higher priority is served first. |
| `battery_device`, `charging_station` | `capacity` | Supply power while on. |
| `keycard_reader`, `access_terminal`, `security_terminal` | `door`, or `credentials` (comma list), `clearance`, `consume`, `requires_power` | Checks the player's cards and keys. Opens `door` and pulses its links on success. |
| `hand_scanner` | `door`, or `hands` / `tags` (comma lists), `requires_power` | Fire a hand at it. Uses the door's rule when `door` is set. |
| `door_controller` | `door`, `demand` | Anchor of a door. Powering it drives `powered` doors. |
| `pulse_receiver` | — | Woken by the Pulse Hand. It reports a pulse, then stays active and powered for `pulse_power_ticks`. |
| `launch_pad` | `hand_tag`, `requires_power`, `strength` | Launches the player when hit by a hand with `launch_pad`. |
| `attach_point` | — | Self-pull anchor, and a traversal point for puzzles. |
| `hand_pickup` | `pickup` (pickup id), or `hand`, `mode`, `chapter`, `event` | Collects a hand. |
| `checkpoint` | `yaw` | Saves the checkpoint when used or walked over. |
| `chase_trigger` | `chase` | Starts the chase when entered. |
| `story_trigger` | `event`, `collectible`, `dialogue` | Fires, collects or talks on use. Fires the event when entered. |
| `objective_trigger` | `objective`, `mode` (`set`/`complete`) | Updates an objective when entered. |
| `chapter_transition` | `chapter` | Starts a chapter. |
| `trigger_volume` | `repeat`, `location` | Invisible in Survival. Reports "player reached" to puzzles and pulses its links. |
| `monster_spawn` | `monster`, `route` | Spawn point used by `spawn_monster` actions. |
| `route_marker`, `puzzle_controller`, `setup_console`, `timer`, `logic_gate` | — | Helpers. |

Settings every device understands:

* `keep_on_reset=true` makes puzzle resets leave the device alone.
* `keep_sockets=true` keeps inserted parts across resets.
* For entered volumes, `repeat=true` fires every time instead of once per save.

## 4. Links (Connection Device)

A link connects a **source** device to a **destination** device:

```json
{ "id": "playtime:link_7", "name": "Lever opens lab", "from": "playtime:lab_lever", "to": "playtime:lab_ctrl",
  "type": "open_door", "targetRef": "playtime:lab_door", "delayTicks": 0, "invert": false, "enabled": true,
  "conditions": [ { "type": "puzzle_completed", "id": "playtime:fuses" } ] }
```

| Type | Effect on the destination |
| --- | --- |
| `power` | Wires the two devices into one circuit (both directions). |
| `activate` | Follows the source: ON activates, OFF deactivates. With `inputsRequired > 1` it acts as an AND gate. |
| `deactivate`, `toggle` | ON deactivates; ON or pulse flips. |
| `enable`, `disable` | Turns the destination's responsiveness on or off. |
| `open_door`, `close_door`, `lock_door`, `unlock_door` | `targetRef` door, or the destination's `door` setting. |
| `start_chase`, `stop_chase`, `trigger_event`, `reset_puzzle` | `targetRef`, or the destination's `chase` / `event` / `puzzle` setting. |
| `puzzle_input` | Reports the signal to puzzles watching the destination. |

Safety rules:

* A link cannot connect a device to itself, or to a device that doesn't exist.
* A link cannot duplicate an existing link.
* A link cannot create an instant loop. Add a delay if you want an oscillator.
* At run time a signal chain stops after 32 steps, and each tick delivers at most 4096 signals. Either
  limit is logged.

`/playtime connections [device]` lists links and the device's state. `/playtime test <device>` pulses a
device.

## 5. Conditions, triggers and actions

**Conditions** are used by doors, puzzles, chases, events, dialogue options and links.

```json
{ "type": "all", "of": [ { "type": "has_hand", "id": "playtime:green_hand" },
                         { "type": "not", "condition": { "type": "flag", "id": "playtime:alarm_on" } } ] }
```

Types: `always`, `all`, `any`, `not`, `flag`, `world_flag`, `has_hand`, `selected_hand`, `has_key`,
`puzzle_completed`, `event_triggered`, `objective_completed`, `objective_active`, `chapter`,
`device_powered`, `device_active`, `door_open`, `chase_completed`, `collectible`, `in_area` (with
`area`).

**Triggers** start story events and chases: `manual`, `enter_area` (with `area`, or the id of a trigger
block), `item_pickup`, `collectible`, `puzzle_completed`, `door_opened`, `device_signal`, `timer`,
`dialogue_finished`, `chapter_start`, `hand_unlocked`, `chase_completed`, `player_join`.

**Actions** share the fields `id`, `text`, `subtitle`, `ticks`, `delay`, `value`, `on`, `pos`, `area`,
`sound`, `volume` and `params`:

| Group | Types |
| --- | --- |
| Presentation | `title_card`, `subtitle`, `dialogue`, `play_sound`, `stop_sound`, `play_recording`, `particles`, `camera_shake`, `screen_fade`, `credits` |
| Progress | `set_flag`, `clear_flag`, `set_world_flag`, `clear_world_flag`, `objective_set`, `objective_complete`, `objective_fail`, `journal_add`, `grant_hand`, `give_key`, `take_key`, `give_item`, `set_checkpoint`, `set_chapter`, `chapter_complete`, `game_complete` |
| World | `door_open`, `door_close`, `door_lock`, `door_unlock`, `lights` (`area` and/or a device `id`, `on`), `emit_signal`, `set_device`, `reset_puzzle`, `spawn_monster` (monster id with `pos`, or a spawn-marker id), `despawn_monster`, `start_chase`, `stop_chase`, `trigger_event`, `teleport_to_checkpoint`, `return_to_menu` |

`delay` queues an action; queued actions are saved with the world. One failing action is logged and
the rest still run.

## 6. Content kinds

### Hands (`hands/`)

```json
{ "id": "mymap:magnet_hand", "displayName": "Magnet Hand", "description": "Pulls metal crates.",
  "icon": "mymap:textures/gui/magnet.png", "model": "mymap:geo/magnet.geo.json", "texture": "mymap:textures/entity/magnet.png",
  "color": "#FF8040C0", "launcher": "right", "grabPacks": ["playtime:grabpack_2"],
  "abilities": ["playtime:grab", "playtime:pull"], "targets": ["pullable", "handle"], "interactions": ["mymap:magnetic"],
  "range": 20, "cooldownTicks": 20, "charges": 0, "rechargeTicks": 100, "energyCost": 0,
  "cable": { "maxLength": 20, "fireSpeed": 2.5, "retractSpeed": 3, "maxAttachTicks": 600 },
  "unlock": { "chapters": [], "flags": [], "hands": [], "puzzles": [], "lockedMessage": "Not yet." },
  "upgrades": [ { "requiresFlag": "mymap:magnet_upgrade", "range": 30, "addAbilities": ["playtime:self_pull"] } ],
  "replaces": null, "tutorial": "mymap:tutorial/magnet", "params": { "pull_strength": 0.9 },
  "animations": { "fire": "animation.magnet.fire" }, "sounds": { "fire": "mymap:magnet.fire" }, "sortOrder": 10 }
```

Built-in abilities: `grab`, `pull`, `interact`, `conduct`, `store_charge`, `launch_pad`, `flare`,
`scanner_access`, `pressure_blast`, `pulse`, `self_pull`, `retrieve`. Target kinds: `interactive`,
`lever`, `handle`, `button`, `scanner`, `power_source`, `power_receiver`, `pullable`, `collectible`,
`attach_point`, `launch_pad`, `pulse_receiver`, `breakable`, `surface`.

Ability parameters (`params`): `charge_ticks`, `power_ticks`, `flare_light_ticks`, `scare_radius`,
`pulse_radius`, `pulse_power_ticks`, `pull_strength`, `blast_strength`.

Hands only work on blocks the map marks as compatible: scanners list hands or tags, launch pads list a
`hand_tag`, and only `pulse_receiver` devices react to pulses.

### Pickups (`pickups/`)

`{ "id", "hand", "mode": "permanent|reusable|chapter_limited|shared_all", "chapter", "objective", "event", "autoEquip" }`

### Credentials (`credentials/`)

`{ "id", "displayName", "kind": "key|keycard|token", "color", "clearance", "model", "description", "trackInProgress" }`

### Doors (`doors/`)

```json
{ "id": "playtime:lab_door", "type": "hand_scanner", "operation": null, "anchor": {"x":10,"y":64,"z":3},
  "width": 2, "height": 3, "facing": "east", "openTicks": 0, "permanentUnlock": true, "startsOpen": false,
  "animationTicks": 12, "access": { "credentials": [], "minClearance": 0, "consumeCredential": false,
  "requiredHands": [], "anyHands": [], "scanHands": ["playtime:green_hand"], "scanTags": [],
  "requiresPower": true, "conditions": [], "deniedMessage": null } }
```

Types: `locked`, `key`, `keycard`, `security_clearance`, `hand_scanner`, `multi_hand`, `powered`,
`circuit`, `button`, `timed`, `heavy_mechanical`, `story`, `chase`, `puzzle`. Each type sets a default
operation:

* `player`: the player uses the door and the access rule decides.
* `signal`: only links and actions open it.
* `power`: the door is open while its controller has power.

Timed doors default to 200 ticks open. Heavy doors animate slowly.

### Puzzles (`puzzles/`)

Common fields: `id`, `displayName`, `template`, `settings`, `requiredItems`, `requiredHands`,
`activation`, `success`, `timeLimitTicks`, `maxAttempts`, `reset`, `resetDevices`, `persistent`,
`linkedDevices`, `linkedDoors`, `onStart`, `onComplete`, `onFail`, `rewards`, `completeSound`,
`failSound`, `completeParticles`. `reset` is `on_failure`, `on_death`, `manual` or `never`.

| Template | `settings` |
| --- | --- |
| A `playtime:power_routing` | `receivers`, `nodes`, `minActiveNodes`, optional `order`, `failOnWrongOrder` |
| B `playtime:fuse_collection` | `fuseBox`, `slots` (`{slot: item}`) |
| C `playtime:keycard` | `reader`, `credentials`, `minClearance`, `requiresPower` |
| D `playtime:color_sequence` | `buttons`, optional `lights` and `tones` (`{button: device/sound}`), `sequence` or `length`, `stepTicks` |
| E `playtime:timed_switches` | `switches`, `windowTicks` |
| F `playtime:traversal` | `points` (trigger volumes, attach points, launch pads), `ordered` |
| G `playtime:machine_assembly` | `parts` (`{"device#slot": item}`), `activator`, `machine`, `requiresPower` |
| H `playtime:hand_scanner` | `scanner`, `hands`, `tags`, `requiresPower` (add story requirements in `success`) |
| I `playtime:multi_room` | `active`, `powered`, `puzzles` |
| J `playtime:staged` | `stages: [{name, template, settings, timeLimitTicks, failsPuzzle, resetDevices, onEnter, onComplete, onFail}]` |
| `playtime:pulse_receivers` | `receivers`, `windowTicks`, `ordered` |

Rules that apply to every template:

* A puzzle is `inactive` until its `activation` conditions hold.
* It completes exactly once, so in multiplayer the rewards are not paid twice.
* Everyone who contributed gets credit, or the shared save does in co-op modes.
* Linked doors open and unlock permanently for those players.

### Monsters (`monsters/`) and routes (`routes/`)

Monster fields:

* **Identity and look:** `id`, `displayName`, `hostile`, `behaviour`, `traits`, `model`, `texture`,
  `animationFile`, `animations{idle,walk,run,sprint,turn,search,attack,intro,stunned,ambush,crawl,hidden,retreat,despawn}`,
  `sounds{ambient,alert,attack,footstep,intro,stunned}`, `ambientParticle`, `width`, `height`.
* **Senses:** `visionRange`, `fovDegrees`, `hearingRange`, `needsLineOfSight`, `memoryTicks`,
  `proximityRange`.
* **Movement:** `walkSpeed`, `chaseSpeed`, `sprintSpeed`, `sprintDurationTicks`, `sprintCooldownTicks`,
  `turnSpeedDegrees`.
* **Attacks:** `attackRange`, `attackCooldownTicks`, `attackDamage`, `captureOutcome` (`damage`, `kill`
  or `catch`; `catch` fails the active chase or returns the player to their checkpoint).
* **Stuns and noise:** `stunnedBy` (hand abilities), `stunTicks`, `distractedBy` (noise tags).
* **Searching and recovery:** `investigateTicks`, `searchTicks`, `patrolRoute`, `recovery`
  (`teleport`, `skip_waypoint`, `return_to_route`, `wait` or `despawn`), `stuckTicks`, `thinkInterval`.
* **Spawning and encounters:** `spawnConditions`, `maxInstances`, `despawnFlag`, `despawnWhenChaseEnds`,
  `introEvent`, `onCatch`, `dialogue`.
* **Profile-specific numbers (`params`):** `ambush_radius`, `intro_ticks`, `chase_duration`,
  `watched_angle`, `retreat_distance`, `vision_factor`, `talk_range`, `patrol_wait`, `aggressive`.

Behaviours:

* `stalker` patrols, chases on sight and searches the last known position.
* `crawler` moves fast along routes.
* `stealth` advances only while unwatched and backs off when looked at.
* `ambusher` hides until a player comes close, springs out, chases briefly, then returns.
* `hunter` remembers longer and searches longer.
* `sound_hunter` is nearly blind and hunts by noise.
* `swarm` chases on any noise and is easily stunned.
* `boss` and `scripted` act only when a chase or story drives them.
* `npc` never attacks.

Routes: `{ "id", "loop", "points": [ { "pos": {...}, "waitTicks", "speed", "animation" } ] }`.

### Chases (`chases/`)

Chase fields:

* **Setup:** `id`, `name`, `trigger`, `monster`, `spawn`, `startDelayTicks`, `route`, `pursuitSpeed`,
  `recovery`.
* **Actions:** `onStart`, `onComplete`, `onFail`.
* **Stages:** `stages[{id, objective, timeLimitTicks, success, branches[{conditions, next, actions}], next, onEnter, onSuccess, checkpoint, monsterRoute, monsterSpeed}]`.
* **Failure:** `failure` (`respawn_at_checkpoint`, `restart_chase`, `reset_puzzles` or
  `return_to_menu`), `checkpoint`, `resetPuzzles`.
* **Other:** `repeatable`, `maxDurationTicks`, `despawnOnEnd`, `music`.

How a chase runs:

* Branches are checked before success conditions. `next` may be a stage id, `complete` or `fail`.
* With several participants, a stage condition holds if it holds for any of them.
* An active chase is not saved; after a restart, it can be triggered again.
* If the chase monster is removed by stuck recovery, it is respawned so the chase never freezes.

### Story (`events/`, `dialogues/`, `collectibles/`, `objectives/`, `chapters/`, `checkpoints/`)

* **Event:** `{ id, trigger, conditions, actions, repeat: once_per_save|once_per_world|always, cooldownTicks }`.
* **Dialogue:** `{ id, start, nodes: { name: { speaker, portrait, text, voice, options[{text, next, conditions, actions}], next, actions } } }`.
  Options whose conditions fail are not sent to the player, and choices are re-checked on the server.
* **Collectible:** `{ id, kind: note|vhs_tape|audio_log|photo|toy|item, title, text, audio, durationTicks, chapter, onCollect }`.
* **Objective:** `{ id, text, chapter, hidden, order }`.
* **Chapter:** `{ id, name, subtitle, order, startCheckpoint, next, hiddenUntilReached, onStart, onComplete }`.
* **Checkpoint:** `{ id, name, pos, yaw, chapter, autoSave }`. A placed Checkpoint Block works too.

### Player Setup profiles (`profiles/`, `profile_assignments/`)

Profile fields:

* **GrabPack and hands:** `giveGrabPack`, `grabPack`, `startingHands`, `startingLeftHand`,
  `startingRightHand`.
* **Items:** `inventory` (`[{item, count, slot, components}]`), `keys`, `batteries`.
* **Where to start:** `startingChapter`, `startingCheckpoint`, `completedPuzzles`, `doorAccess`, `flags`.
* **Abilities:** `canSprint`, `canCrouch`, `abilities`.
* **Overrides:** `showMainMenu`, `keepItemsOnDeath`, `keepHandsOnDeath`, `sharedProgression`,
  `tutorial`.
* **Restrictions:** `deniedInteractions` (`playtime:device/<type>` or a device id).
* **Other:** `extra`.

Assignment: `{ id, type: player|team|area|scenario, match, area, profile, priority }`. A profile set on a
player by command wins. Otherwise the highest-priority matching rule applies, and then the world's
`defaultProfile`.

## 7. Commands (`/playtime`)

All commands except opening your own menu need operator permission (checked on the server). Each one is
backed by a method in `AdminService`:

| Command | Purpose |
| --- | --- |
| `inspect <player>` | Show a player's progress. |
| `hand give\|revoke <player> <hand>` | Give or remove a hand. |
| `checkpoint <player> <id> [tp]` | Set a checkpoint, optionally teleporting. |
| `save reset <player> [world]` | Reset a test save (and the world's devices, doors and puzzles). |
| `puzzle reset [id]` | Reset one puzzle, or all. |
| `puzzle status [id]` | Show puzzle states. |
| `chase start\|stop <id>` | Run or stop a chase. |
| `monster spawn <id> <pos>` | Spawn a monster. |
| `monster list` | List monster definitions and live instances. |
| `monster despawn` | Remove all engine-managed monsters. |
| `event <id> [player]` | Fire a story event (ignores repeat rules). |
| `connections [device]` | List links and device state. |
| `link remove\|rename <id>` | Edit links. |
| `test <device>` | Pulse a device. |
| `validate` | Check the whole map. |
| `reload` | Reload content files. |
| `backup` | Copy config, state and content into `backups/`. |
| `menu [player]` | Open the menu for a player. |
| `menu bypass on\|off [player]` | Skip the menu while testing. |
| `profile list\|assign\|apply` | Manage setup profiles. |
| `sample install` | Install the sample game at your position. |
