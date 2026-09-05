# MiraCrates

MiraCrates is the crate, key and reward engine for the Mira Paper server suite. It provides deployable physical crates, configurable reward pools and chances, player previews, keyed openings, admin editing tools, opening history and optional holograms.

## Download

[**Download MiraCrates v0.3.7**](https://github.com/FiveSOCE/Mira-Crates/releases/download/v0.3.7/MiraCrates-0.3.7.jar)

[View All Releases](https://github.com/FiveSOCE/Mira-Crates/releases)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.1.0 or newer
- Holograms optional for floating crate-name labels
- MiraSpawners 0.1.3 or newer optional for native MiraSpawners rewards
- PlaceholderAPI optional
- MiraNPC optional integration

## How MiraCrates Works

Administrators create crate definitions containing a physical crate appearance, accepted key and reward pool. Item reward chances are edited explicitly and must total exactly 100.00% before the crate can be saved. Adding or removing a reward does not automatically rebalance existing percentages; new rewards start at 0.00% unless an administrator deliberately changes or auto-balances them.

A deployed crate is represented by a protected shulker and its location is persisted. Normal players left-click a placed crate to preview rewards and right-click it with the correct key to open it. Crouch + right-click supports the quick-open path. Normal players cannot break, move or edit deployed crates. When the Holograms plugin is available, deployed crates receive a floating configured display name.

The admin GUI is organized around Create, Manage and Keys. Existing deployed crates can be converted to another crate definition with `/mcrate change <crate>`, updating the crate identity, shulker colour, accepted rewards/key data, saved location identity and hologram. Data is persisted under `plugins/MiraCrates/`, including crate definitions, keys, locations, player data and opening history.

## Commands

The entire administration command surface requires `miracrates.admin`.

| Command | Permission | What it does |
| --- | --- | --- |
| `/mcrates` | `miracrates.admin` | Opens the MiraCrates admin GUI. |
| `/mcrates create` | `miracrates.admin` | Starts/opens crate creation. |
| `/mcrates givecrate <crate>` | `miracrates.admin` | Gives a deployable copy of an existing crate definition. |
| `/mcrate change <crate>` | `miracrates.admin` | Converts the deployed crate you are looking at to another existing crate definition. |
| `/mcrates remove` | `miracrates.admin` | Removes the targeted deployed crate/admin-selected crate. |
| `/mcrates info` | `miracrates.admin` | Shows MiraCrates runtime/configuration information. |
| `/mcrates test` | `miracrates.admin` | Runs MiraCrates diagnostics/self-tests. |
| `/mcrates reload` | `miracrates.admin` | Reloads MiraCrates configuration and rebuilds supported runtime state such as holograms. |
| `/mcrates help` | `miracrates.admin` | Shows MiraCrates command help. |

Aliases: `/miracrates`, `/mcrate`.

Player crate interaction is not command-driven: previewing uses `miracrates.preview` and opening a crate with a valid key uses `miracrates.use`.

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miracrates.admin` | OP | Allows the admin GUI, crate editor, crate/key tools, physical crate conversion/removal and diagnostics. |
| `miracrates.use` | Everyone | Allows opening deployed crates with a valid key. |
| `miracrates.preview` | Everyone | Allows previewing deployed crate rewards by left-clicking. |


## MiraCosmetics Integration (0.3.5)

Adds MiraCosmetics crate opening and reward-rarity visuals and fixes the stale CI artifact/release version path.

## MiraCosmetics Audio Integration (0.3.6)

MiraCosmetics audio hooks add rising opening plings, rarity-specific reward sounds and a 20-block audio-only celebration for Legendary/Mythic rewards while preserving the opener's existing reward visuals.


## Crate Audio Audience (0.3.7)

Opening, common and rare crate sounds remain actioning-player only.

Legendary/mythic reward audio is now server-wide while the legendary visual effect remains scoped to the player opening the crate.
