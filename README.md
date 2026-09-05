# MiraCrates

MiraCrates is the crate, key and reward engine for the Mira Paper server suite. It provides deployable physical crates, configurable reward pools and chances, player previews, keyed openings, admin editing tools, opening history and optional holograms.

## Download

[**Download MiraCrates v0.3.12**](https://github.com/FiveSOCE/Mira-Crates/releases/download/v0.3.12/MiraCrates-0.3.12.jar)

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


## Crate UX Polish (0.3.9)

- opening animation is now a faster CS2-style 9-item horizontal slider with the real pre-rolled reward landing in the center selector
- reward chance is now the sole probability authority; changing rarity does not secretly alter the configured chance
- Shift-left-click a reward in the crate editor to cycle Common, Rare, Legendary and Mythic rarity categories
- existing reward rarity is preserved when editing and saved back correctly
- Common and Rare use their matching reward sounds; Legendary and Mythic share the existing Legendary/Mythic celebration path
- crate previews preserve the real reward ItemStack metadata, including custom item names, lore, enchantments, model/PDC data and other metadata
- player previews no longer display rarity or sort by rarity
- every visible reward always appends its exact chance percentage
- Mythic is automatically added as a built-in rarity option on existing installations without overwriting existing rarity definitions


## Console Command Rewards (0.3.10)

The Edit Crate GUI now exposes MiraCrates' existing server-console reward backend.

- click **Add Command Reward**
- type the console command in chat without the leading slash
- use `%player%` for the player who wins the reward
- command rewards appear in the same reward grid as item rewards
- left-click edits chance
- Shift-left-click changes rarity
- right-click removes the reward
- existing COMMAND rewards are loaded into the editor instead of being hidden/preserved-only
- commands execute as the server console only after the crate result is finalized
- normal player previews show the configured command reward presentation, not the raw server command


## Longer Synced Case Reel (0.3.11)

- keeps the current fast CS2-style reel speed
- extends the default slider from 24 to 40 movement steps
- existing installs using the old untouched 24-step default are migrated to 40 automatically
- manually customised slider-step values are preserved
- opening audio is now fired once per actual reel movement instead of using the old independent timed sequence


## Reward Naming & Command Voucher Presentation (0.3.12)

- crate win chat now uses the actual ItemStack display name for item rewards, preserving custom colours/components
- unnamed vanilla items fall back to their proper translated Minecraft item name instead of material/config placeholders
- jackpot/rare-win broadcasts also preserve the actual reward item name
- command rewards are displayed as PAPER rather than command blocks
- creating a command reward now asks for the console command and then its player-facing reward name
- command reward names support colour codes
- command rewards can be renamed later from the reward detail/chance editor
- the configured command reward name is used consistently in the editor, preview, slider and win chat
