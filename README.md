# MiraCrates

MiraCrates is the GUI-first crate, key and reward engine for the Mira Minecraft plugin ecosystem. It targets **Paper 1.21.11** and **Java 21** and integrates directly with **MiraCore**.

## Download

[**Download MiraCrates v0.3.2 (.jar)**](https://github.com/FiveSOCE/Mira-Crates/releases/download/v0.3.2/MiraCrates-0.3.2.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Crates/releases)

Current release: **v0.3.2**

## v0.3.2 crate holograms

When the **Holograms** plugin is installed and enabled, every deployed MiraCrates shulker receives a floating label above it showing that crate's configured display name.

- The hologram appears automatically when a crate is placed.
- Existing deployed crates are rebuilt from `locations.yml` after restart or `/mcrates reload`.
- The hologram is removed when an administrator picks up/removes the crate.
- The label uses the crate display name from `crates.yml`, including legacy `&` colour formatting.
- Hologram height is configurable in `config.yml`.

Default configuration:

```yml
holograms:
  enabled: true
  height: 1.65
```

Holograms is a soft dependency, so MiraCrates continues to function if it is missing; only the floating labels are disabled.

## Physical crate shulkers

Give yourself an existing crate with:

```text
/mcrates givecrate <crate name>
```

Place the special shulker anywhere and it becomes a working crate immediately.

By default:

- **Left-click** the crate to preview rewards.
- **Right-click while holding the correct key** to open it normally.
- **Crouch + right-click while holding the correct key** to quick-open it.
- Empty hand or the wrong key cannot open the crate.
- Normal players cannot break placed crate shulkers.
- Administrators can break one and receive the deployable crate shulker back.
- Placed crates are protected from explosions and piston movement.

Administrators can remove one deployed crate instance with:

```text
/mcrates remove
```

Look directly at the placed MiraCrates shulker and run the command. The physical shulker is removed and its saved location is unregistered. The crate definition, companion key, rewards, and any other placed copies remain untouched.

## Creating a crate

Run:

```text
/mcrates create
```

or open `/mcrates` and click **Create New Crate**.

The creator GUI lets an administrator configure:

- crate display name
- shulker colour
- item rewards
- exact percentage chance for each item
- automatic chance balancing

Every crate automatically receives a matching physical key when the crate definition is created. Existing older crates without a valid key are migrated on load.

## Opening modes

### Normal opening

Normal opening consumes one valid key, locks the reward before animation begins, then runs the roulette GUI.

### Quick opening

Hold the correct key, crouch and right-click the crate. MiraCrates consumes one key, rolls the reward through the same reward engine and grants it immediately without opening the roulette GUI.

Quick opening does not bypass key requirements, crate permissions, cooldowns, reward eligibility or reward logging.

## Reward engine

The engine supports:

- item rewards
- console-command rewards
- MiraSpawners rewards through its registered API
- MiraCrates key rewards
- XP-level rewards
- permission-aware rewards
- physical and virtual keys
- crate cooldowns
- opening history

## Main commands

```text
/mcrates
/mcrates create
/mcrates givecrate <crate name>
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
- Holograms 1.0.1 is optional and enables crate-name holograms
- MiraSpawners 0.1.3 or newer is optional and enables native MiraSpawners reward support

Holograms resource: https://www.spigotmc.org/resources/holograms-1-21-1-21-11.130705/

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `miracrates.admin` | OP | Editor, crate placement recovery and administration |
| `miracrates.use` | Everyone | Open placed crates with a valid key |
| `miracrates.preview` | Everyone | Preview placed crate rewards |

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
build/libs/MiraCrates-0.3.2.jar
```

GitHub Actions compiles and tests MiraCrates with Java 21 against Paper 1.21.11.
