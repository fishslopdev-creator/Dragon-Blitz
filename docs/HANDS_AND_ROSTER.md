# Hands and character roster: research notes

The built-in definitions follow each hand's verified role in the released chapters. Where a role could
not be confirmed, it is noted here and left configurable rather than guessed.

| Hand | Engine abilities | Basis |
| --- | --- | --- |
| Blue Hand (left) | grab, pull, interact, conduct, retrieve | Chapter 1 starting hand. The GrabPack grabs, pulls and collects objects and opens GrabPack doors, and its cable conducts power between sources and receivers. |
| Red Hand | grab, pull, interact, conduct, retrieve; works red scanners | Chapter 1. Taken from the player in Chapter 2. |
| Green Hand | grab, interact, conduct, store_charge | Chapter 2. Transfers electricity between sources by holding a charge. |
| Purple Hand | grab, launch_pad | GrabPack 2.0. Wiki: launches its user by activating purple jump pads (also called the Rocket Hand). |
| Flare Hand | flare (light, scare, burn flammable) | GrabPack 2.0. Wiki: shoots flares that light the area, can scare off enemies and burn certain resources. Obtained in Chapter 3. |
| Omni-Hand | Red Hand abilities plus store_charge, scanner_access, 4 charges; replaces the Red Hand | Chapter 4. An upgraded Red Hand made with the Omni-Chip. Guides describe holding four charges and opening red scanners and certain doors. |
| Pressure Hand | grab, pressure_blast (charge, then release) | Chapter 5. Attaches to a surface, charges, then releases a blast that pushes objects and buttons and breaks vents, glass and ice. |
| Conductive Hand | grab, conduct (live conduit) | Chapter 5. Described as a continuous power conduit you keep active while moving between nodes. One wiki also mentions carrying fire and ice; that was not confirmed elsewhere, so it is **not** implemented and can be added later as an upgrade. |
| Pulse Hand | pulse, interact | Original to this engine. |

Chapter numbering differs between sources (Deep Sleep is called Chapter 3 by one source and Chapter 4
by another). Hands are not tied to chapters in code; use pickups and profiles to place them in your map.

## Roster

Every entry is a configurable `MonsterDefinition` with a behaviour profile chosen to match the
character's role:

* **Chapter 1:** Huggy Wuggy (stalker).
* **Chapter 2:** Mommy Long Legs (crawler), Bunzo Bunny and PJ Pug-a-Pillar (scripted set pieces),
  Mini Huggies (swarm).
* **Chapter 3:** CatNap (stealth, red smoke), Miss Delight (stalker), DogDay (NPC), hostile Smiling
  Critters (swarm).
* **Chapter 4:** Boxy Boo (ambusher), Yarnaby (hunter), Pianosaurus (sound hunter), the Doctor (boss).
* **Chapter 5:** the Prototype (boss), Huggy Wuggy's return (hunter), Lily Lovebraids (stalker),
  Wrongside Outimals (swarm).
* **Optional NPCs:** Poppy, Kissy Missy, Giblet, Chum Chompkins.
* **Original:** The Playwright (hunter).

Only The Playwright has a model and animations so far. The other characters need original models
(Blockbench) before they can be shown; the definitions already reference the expected file paths.
Character profiles for the Chapter 5 characters (Lily Lovebraids, the Wrongside Outimals, Giblet, Chum
Chompkins) could not be researched in depth, so they use generic profiles. Adjust their behaviour,
speeds and outcomes per map.

## Sources

* [poppyplaytime.wiki.gg: GrabPack](https://poppyplaytime.wiki.gg/wiki/GrabPack)
* [Poppy Playtime Wiki: GrabPack 2.0](https://poppy-playtime.fandom.com/wiki/GrabPack_2.0)
* [Poppy Playtime Wiki: Old GrabPack](https://poppy-playtime.fandom.com/wiki/Old_GrabPack)
* [Poppy Playtime Wiki: Flare Hand](https://poppy-playtime.fandom.com/wiki/Flare_Hand)
* [Poppy Playtime Wiki: Omni-Chip](https://poppy-playtime.fandom.com/wiki/Omni-Chip)
* [Poppy Playtime Wiki: Chapter 5: Broken Things](https://poppy-playtime.fandom.com/wiki/Chapter_5:_Broken_Things)
* [Wikipedia: Poppy Playtime](https://en.wikipedia.org/wiki/Poppy_Playtime)
* [Insider Gaming: how to get the Pressure Hand](https://insider-gaming.com/how-to-get-pressure-hand-in-poppy-playtime-chapter-5-locker-code-explained/)
* [Game-Meta: Chapter 5 GrabPack upgrades](https://game-meta.com/poppy-playtime-chapter-5-grabpack-upgrades-puzzle-solutions/)
* [itemlevel.net: acquiring the Omni-Hand](https://itemlevel.net/poppy-playtime-acquire-the-omni-hand-upgrade-chapter-4-safe-haven)
* [Fabric for Minecraft 1.21.11](https://fabricmc.net/2025/12/05/12111.html)
