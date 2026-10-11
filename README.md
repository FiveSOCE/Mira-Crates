# MiraCrates

Crate, key and reward engine for the Mira Paper server suite.

MiraCrates provides deployable physical crates, configurable reward pools, Random and Choice opening modes, player previews, command/item rewards, multi-win openings, opening history, optional holograms and resource-pack-aware crate keys.

## Current Release

**v0.3.20** is the current release. A compatibility branch is being prepared for separate Legacy and Modern build targets; those targets are not yet declared production-supported until both builds and runtime checks pass.

[View releases](https://github.com/FiveSOCE/Mira-Crates/releases)

## Compatibility targets in development

The compatibility branch is preparing two separate artifacts from a shared codebase:

| Artifact | Intended target | Java baseline |
| --- | --- | --- |
| `MiraCrates-Legacy` | Paper 1.18.2 and compatible legacy releases | Java 17 |
| `MiraCrates-Modern` | Paper 26.2 and compatible modern releases | Java 25 |

These are build targets under development, not a claim of confirmed compatibility. Both need successful CI builds and in-server testing before being advertised as supported.

## Current integrations

The current release still requires MiraCore. Holograms, MiraSpawners, PlaceholderAPI, MiraNPC, and MiraCosmetics are optional integrations where supported. Removing the required MiraCore runtime dependency is part of the standalone refactor and is not yet complete.

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

Modern target:

```bash
gradle clean test build -Ptarget=modern
```

Legacy target:

```bash
gradle clean test build -Ptarget=legacy
```

The output JAR is created in `build/libs/`. These targets are under development and may currently fail until version-specific API use and runtime dependencies are refactored.

## License

**All Rights Reserved.** Copyright (c) 2026 FiveSOCE. This repository is proprietary. No permission to use, copy, modify, or redistribute the software is granted without prior written permission, except as required by applicable law. See [LICENSE](LICENSE).
