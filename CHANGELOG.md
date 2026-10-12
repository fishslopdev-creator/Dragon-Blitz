# Changelog

## 0.1.0 (unreleased)

### Added
* Engine core (`engine-core`), Java 21, no Minecraft dependency:
  * per-world configuration, main-menu trigger policy, save slots, shared and host-owned progression,
    New Game confirmation, death rules, versioned saves with migration;
  * Player Setup profiles and automatic assignment by player, team, area or scenario;
  * hand registry, upgrades, pickups, wheel data, server-validated selection;
  * GrabPack cable simulation and hand abilities;
  * nine hands, including the original Pulse Hand;
  * keys, keycards, clearance levels, hand scanners and 14 door types;
  * connection graph with loop prevention; power solver;
  * puzzle engine with templates A–J plus pulse receivers;
  * monster brain with 10 behaviour profiles; a roster of 22 definitions, including the original
    Playwright;
  * chase controller; story events, dialogue, collectibles, objectives, chapters and checkpoints;
  * map validator, admin command logic, request rate limiting, backups;
  * sample game: the Assembly Line puzzle and the Factory Shutdown chase.
* 106 unit and integration tests, including a full headless playthrough of the sample.
* Fabric module: original models, textures, animations, GUI art and synthesized sounds; block and item
  models; language file; Fabric metadata; a Loom build file (disabled by default).
* Documentation: README, map-making reference, architecture, Fabric integration plan, research notes.

### Not yet done
* The Fabric game layer (Java) and all in-game testing. See `docs/FABRIC_INTEGRATION.md`.
* Models for the characters other than The Playwright.
