# MiraCrates

Crate, key and reward engine for the Mira Paper server suite.

MiraCrates provides deployable physical crates, configurable reward pools, Random and Choice opening modes, player previews, command/item rewards, multi-win openings, opening history, optional holograms and resource-pack-aware crate keys.

## Current Release

**v0.3.20** — compatible with Paper/Minecraft **1.21.11 through 26.2** using Java 21 bytecode.

[View releases](https://github.com/FiveSOCE/Mira-Crates/releases)

## Requirements / Integrations

- Paper 1.21.11 through 26.2
- Java 21
- MiraCore
- Holograms optional for floating crate labels
- MiraSpawners optional for native typed-spawner rewards
- PlaceholderAPI optional
- MiraNPC optional integration
- MiraCosmetics optional for crate visuals/audio

## Core Features

- physical protected shulker-based crates
- physical and virtual key support
- crate-specific companion keys
- item rewards preserving full ItemStack metadata
- console command rewards with player-facing names
- Common, Rare, Legendary and Mythic rarity presentation
- CS2-style horizontal opening reel
- 1-5 sequential wins per opening
- Random and Choice opening modes
- unlimited practical reward-pool size through pagination
- player reward preview GUI
- optional holograms
- opening history and persisted player data
- admin create/manage/key GUI

## Reward Pools

Random crates use explicit reward chances. Item and command rewards can coexist in the same pool. The editor requires Random-mode chances to total exactly 100.00% before the crate can be saved.

Reward pools are no longer capped at 18 entries. The admin editor paginates 18 rewards at a time, while preview/opening logic continues to use the entire configured pool.

Choice crates ignore percentage chances and let the player select unique eligible rewards. `Wins Per Open` controls how many choices may be made, from 1 through 5.

## Opening Modes

### Random

- consumes a valid key
- pre-rolls the winning reward
- animates a CS2-style horizontal reel
- supports 1-5 sequential reward rolls
- each roll is delivered independently
- reward rarity controls presentation, not probability

### Choice

- configured per crate
- presents eligible rewards directly to the player
- supports 1-5 unique selections
- selections persist across reward pages
- key is consumed only when the player confirms the choices
- reward percentages are hidden/ignored

## Large Reward Pools — v0.3.19

The old 18-reward admin limit has been removed.

- crate editing uses paginated 18-reward pages
- Choice mode supports paginated 45-reward selection pages
- Random, multi-win and preview flows use the full pool
- existing crate definitions remain compatible

## Crate Definition Management — v0.3.20

The Manage GUI now supports safe crate-definition deletion.

- press the configured drop/Q action on a crate in Manage to enter the delete confirmation flow
- deployed copies must be removed first so saved physical crate locations are not orphaned
- unused auto-generated companion-key data is removed with the crate definition

Renaming an existing crate now also refreshes the name/lore of its auto-generated companion key. Custom/shared keys are not renamed accidentally.

## Key Distribution

Recent key tooling includes:

```text
/mcrates keyall <key>
/mcrateskey <player> <key> [amount]
```

Aliases for the direct grant command include `/mkeygive` and `/mcratekey`.

Physical crate keys can use the permanent resource-pack model key:

```text
mira:crate_key
```

## Commands

The administration surface requires `miracrates.admin` unless otherwise noted.

| Command | Purpose |
| --- | --- |
| `/mcrates` | Opens the admin GUI. |
| `/mcrates create` | Starts/opens crate creation. |
| `/mcrates givecrate <crate>` | Gives a deployable crate item. |
| `/mcrates keyall <key>` | Gives one key to every online player. |
| `/mcrateskey <player> <key> [amount]` | Console-safe targeted key grant. |
| `/mcrate change <crate>` | Converts the deployed crate being targeted to another definition. |
| `/mcrates remove` | Removes the targeted deployed crate. |
| `/mcrates info` | Shows runtime/configuration information. |
| `/mcrates test` | Runs diagnostics/self-tests. |
| `/mcrates reload` | Reloads configuration and reconciles supported runtime state. |

Player interaction is GUI/block driven:

- left-click deployed crate: preview rewards (`miracrates.preview`)
- right-click with valid key: open crate (`miracrates.use`)
- crouch + right-click: quick-open path where applicable

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `miracrates.admin` | OP | Administration, editing, grants and diagnostics. |
| `miracrates.use` | Everyone | Opens deployed crates with valid keys. |
| `miracrates.preview` | Everyone | Previews deployed crate rewards. |

## Persistence

MiraCrates stores crate definitions, keys, deployed locations, player state and opening history under:

```text
plugins/MiraCrates/
```

Hologram reconciliation removes stale MiraCrates-owned labels and rebuilds them only for valid saved crate blocks.

## Building

```bash
gradle clean build
```

The output JAR is created in `build/libs/`.
