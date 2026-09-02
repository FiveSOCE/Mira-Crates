# MiraCrates

MiraCrates is the crate, key and reward engine for the Mira Minecraft plugin ecosystem. It targets **Paper 1.21.11** and **Java 21** and integrates directly with **MiraCore**.

## Download

[**Download MiraCrates v0.3.3 (.jar)**](https://github.com/FiveSOCE/Mira-Crates/releases/download/v0.3.3/MiraCrates-0.3.3.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Crates/releases)

Current release: **v0.3.3**

## v0.3.3 editor and admin hardening

The `/mcrates` admin GUI is now intentionally minimal and ordered:

1. **Create**
2. **Manage**
3. **Keys**

The old GUI book/help tile and rarity/nether-star tile have been removed. Rarity data remains an internal reward-engine concern and is no longer exposed in the main GUI.

### Reward chance editing

Editing an existing crate no longer rewrites the existing reward percentages when a reward is added or removed.

- New rewards start at **0.00%**.
- Existing reward chances stay exactly where they were.
- Removing a reward does not rebalance the remaining rewards.
- Save remains locked until the item reward chances total exactly **100.00%**.
- Auto Balance only changes values when the admin explicitly clicks it.
- Chance controls are now **-10%, -1%, -0.01%, +0.01%, +1%, +10%**.

Saving a crate no longer gives the administrator a new crate item. Use **Manage** or `/mcrates givecrate <crate>` when a deployable copy is actually wanted.

### Change a deployed crate

An administrator can look directly at an existing deployed MiraCrates shulker and run:

```text
/mcrate change <crate name>
```

`/mcrates change` and `/miracrates change` are also accepted.

The targeted physical crate is converted to the requested existing crate definition. This updates:

- crate identity
- shulker colour/material
- accepted key/rewards through the new crate definition
- saved placed-crate location data
- floating hologram name

The command requires `miracrates.admin`.

## Player permissions

Normal players do **not** have access to the MiraCrates admin menu, crate editor, key management tools, create/remove/change commands, or other administration commands.

Their normal interaction surface is:

- **Left-click a placed crate** to preview its rewards.
- **Right-click a placed crate while holding the correct key** to open/redeem it.
- **Crouch + right-click with the correct key** performs the existing quick-open path.

Placed crates remain protected from normal-player breaking, explosions and piston movement.

## Crate holograms

When the **Holograms** plugin is installed and enabled, every deployed MiraCrates shulker receives a floating label showing that crate's configured display name.

- Created automatically when a crate is placed.
- Rebuilt from `locations.yml` after restart or `/mcrates reload`.
- Removed with the crate.
- Updated by `/mcrate change`.
- Supports legacy `&` colour formatting.

Default configuration:

```yml
holograms:
  enabled: true
  height: 1.65
```

## Main admin commands

```text
/mcrates
/mcrates create
/mcrates givecrate <crate name>
/mcrate change <crate name>
/mcrates remove
/mcrates help
/mcrates info
/mcrates test
/mcrates reload
```

## Requirements

- Paper 1.21.11
- Java 21
- MiraCore 0.1.0 or newer
- Holograms is optional and enables crate-name holograms
- MiraSpawners 0.1.3 or newer is optional and enables native MiraSpawners reward support

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `miracrates.admin` | OP | All admin GUI, editing, crate/key tools and physical crate change/remove operations |
| `miracrates.use` | Everyone | Open placed crates with a valid key |
| `miracrates.preview` | Everyone | Preview placed crate rewards by left-clicking |

## Data files

```text
plugins/MiraCrates/
├── config.yml
├── crates.yml
├── keys.yml
├── rarities.yml
├── locations.yml
├── playerdata.yml
└── opening-history.log
```

## Building from source

```bash
gradle clean test build
```

Output:

```text
build/libs/MiraCrates-0.3.3.jar
```

GitHub Actions compiles and tests MiraCrates with Java 21 against Paper 1.21.11.
