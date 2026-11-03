<p align="center">
  <img src="docs/banner.png" alt="Smart Chests" width="760">
</p>

<h1 align="center">Smart Chests</h1>

<p align="center">
  <b>One terminal. Every chest.</b> Search all the storage around you from a single screen, then withdraw or deposit without opening a single chest. A Fabric mod for Minecraft Java 26.3.
</p>

<p align="center">
  <a href="../../releases/latest"><img alt="Download" src="https://img.shields.io/badge/Download-latest%20jar-2b6cff?style=for-the-badge"></a>
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.3-3fae49?style=for-the-badge">
  <img alt="Fabric" src="https://img.shields.io/badge/Fabric-required-dbd0b4?style=for-the-badge">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-lightgrey?style=for-the-badge">
</p>

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for **Minecraft 26.3** (Java 25). Works with SKlauncher's Fabric profile.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Put `SmartChests-<version>-mc26.3-fabric.jar` (from the [latest release](../../releases/latest)) in the same folder.

Install it on the server **and** every client (it adds a block and a menu).

## How to play

Craft and place a **Smart Storage Terminal**, then right-click it. No wires, no extra blocks: it finds every **chest, trapped chest, barrel and shulker box** within 16 blocks on its own.

| Area | What it does |
|---|---|
| Top bar | Title, **search box**, **Scan** (refresh) button, number of connected containers |
| Item grid | Icon, name and **total quantity** of every item across all containers. Mouse wheel scrolls |
| Details | Click an item: total, number of containers, and for each one its **#, kind, quantity and coordinates** |
| Filter | All Items, Blocks, Tools, Food, Materials, Redstone, Miscellaneous (click to cycle) |
| Sort | Quantity high-low / low-high, A-Z, Z-A (click to cycle) |
| Take Stack / Take All | Withdraw one stack / everything of the selected item. If your inventory is full the rest stays in storage |
| Deposit Item | Moves every matching item from your inventory and hotbar into the containers |
| Deposit All | Moves your whole inventory and hotbar in. Asks you to click twice to confirm |

The status line reports exactly what happened (for example "Deposited 120. Could not fit: 8.").

### Safety

- Everything is decided by the **server**. The client only sends "what" it wants; the server re-scans, re-checks that you are next to the terminal and recomputes every amount.
- The amount that fits is computed **before** anything is touched. Items are moved with their full data (enchantments, names, durability, components). Anything that cannot be placed goes back into storage, and as a last resort drops at your feet. Nothing is deleted or duplicated.
- Double chests are counted once.
- Chunks are **never force-loaded**. If nearby chunks are not loaded, a yellow `!` appears next to the filters.
- Scans happen only when you open the terminal, press Scan, or make a transfer. Nothing runs per tick.
- Other mods can protect containers: `SmartChestsApi.ACCESS_CHECK` is an event whose listeners can veto any container position.

## Recipe

```
I Q I      I = iron ingot     Q = nether quartz
R H R      R = redstone dust  H = chest
I C I      C = comparator
```

The recipe is unlocked as soon as you join a world, so it shows up in the recipe book.

## Config

`config/smartchests.json`

| Option | Default | Meaning |
|---|---|---|
| `searchRadius` | 16 | Blocks searched around the terminal in every direction (1-32) |

## Build from source

Needs JDK 25.

```
./gradlew build
```

The jar is `build/libs/SmartChests-<version>-mc26.3-fabric.jar`. Pushing to GitHub builds it automatically and publishes a release (see `.github/workflows/build.yml`).

To ship an update: bump `version` in `gradle.properties` and add `.github/changes/<version>.md`.

`tools/generate_textures.py` and `tools/generate_branding.py` (Pillow) regenerate the block textures, banner and icon. `tools/check_resources.py` verifies that every model, texture, recipe and lang key resolves.

## Build status (read this first)

This project was written without being able to run Gradle or Minecraft, so **it has not been compiled or tested in-game yet**. Minecraft 26.x changes a lot between releases (menus, GUI drawing, input, data formats), so the first GitHub build may report a few compile errors. Paste the Actions log and they can be fixed quickly. Test checklist for the first run:

1. Place a terminal and three chests, put diamonds in each, search "diamond": total and three locations should show.
2. Take Stack, Take All, fill your inventory and try again (partial withdrawal).
3. Deposit Item, Deposit All (confirm), full containers, a double chest, close/reopen, filters/sorting, Scan.
4. Multiplayer: two players on one terminal.

MIT licensed.
