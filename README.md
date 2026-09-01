# MiraCrates

MiraCrates is the GUI-first crate, key and reward engine for the Mira Minecraft plugin ecosystem. It targets **Paper 1.21.11** and **Java 21** and integrates directly with **MiraCore**.

## Download

[**Download MiraCrates v0.2.1**](https://github.com/FiveSOCE/Mira-Crates/releases/download/v0.2.1/MiraCrates-0.2.1.jar)

Current release: **v0.2.1**

## v0.2.1 interaction hotfix

v0.2.1 fixes the two primary crate-creator interaction issues from v0.2.0:

- the crate-name anvil now produces a clickable confirmation result while the administrator types, and clicking that result commits the name and returns to the crate editor
- normal left/right clicks in the administrator's own inventory are now allowed while the crate editor is open, so reward items can be picked up onto the cursor; unsafe transfer paths such as shift-click and hotbar swapping remain blocked

## v0.2 crate workflow

Every MiraCrates crate is represented by a designated coloured shulker box.

Normal administration is intentionally GUI-first:

```text
/mcrates
```

opens the MiraCrates dashboard.

```text
/mcrates create
```

opens the crate creator directly.

The creator GUI lets an administrator configure:

- crate display name
- shulker colour
- item rewards
- exact percentage chance for each item
- automatic chance balancing

Reward chances must total exactly **100%** before the crate can be saved. The Auto Balance button divides 100% evenly across the current item rewards, after which individual rewards can be adjusted with the chance editor.

When a new crate is saved, MiraCrates automatically gives the administrator its deployable shulker item.

## Creating a crate

Run:

```text
/mcrates create
```

In the GUI:

1. Click **Crate Name**, type the name in the anvil interface, then click the result name tag to confirm it.
2. Left/right-click **Shulker Colour** to cycle through the sixteen coloured shulker boxes.
3. Use normal left/right clicks in your own inventory to put the reward item on your cursor, then click **Add Reward Item**. The item is copied, not consumed.
4. Click a reward item to edit its chance. Right-click a reward to remove it.
5. Use **Auto Balance Chances** whenever useful.
6. Once the displayed total is exactly 100%, click **Save Crate**.

The crate ID is generated from the original crate name. For example:

```text
Vote Crate -> vote_crate
Legendary Crate -> legendary_crate
```

## Physical crate shulkers

Give yourself any existing crate with:

```text
/mcrates givecrate <crate name>
```

Examples:

```text
/mcrates givecrate vote_crate
/mcrates givecrate Vote Crate
```

The item is a special MiraCrates shulker carrying hidden crate identity data.

Place the shulker anywhere and it becomes a working crate immediately. No location command is needed.

By default:

- **Left-click** a placed crate to preview rewards.
- **Right-click** a placed crate to open it.
- Normal players cannot break placed crate shulkers.
- Administrators can break one to pick the special crate shulker back up.
- Placed crate shulkers are protected from explosions and piston movement.
- The placed block stores its crate identity as tile PDC as well as normal MiraCrates location persistence.

## Managing crates through `/mcrates`

The Crates browser is an editor rather than a read-only list.

On a crate entry:

- **Left-click** to edit the crate through the GUI.
- **Shift-left-click** to preview its rewards.
- **Right-click** to give yourself its deployable shulker.

Editing a crate uses the same name, colour, reward and chance interface as creation.

## Reward engine

GUI-created item rewards use direct percentages. Their configured chances are stored as weights in the single active item reward pool, so a crate configured as:

```text
Diamond x8    50%
Spawner x1    30%
Sword x1      20%
```

has a 50/30/20 item reward split.

The underlying reward engine remains available for advanced integrations, including:

- item rewards
- console-command rewards
- MiraSpawners rewards through its registered API
- MiraCrates key rewards
- XP-level rewards
- permission-aware rewards
- physical and virtual keys
- crate cooldowns
- reward history

Advanced command routes are retained for functionality that has not yet been moved into a dedicated GUI, but they are no longer the intended normal crate-creation workflow.

## Opening safety

The reward is selected and locked before the roulette animation begins.

Closing the inventory does not cancel a pending reward. If a player disconnects during an opening, MiraCrates finishes the payout during the quit flow. If a reward provider fails after a key was consumed, the key is refunded where applicable and the failure is logged.

## Main commands

```text
/mcrates
/mcrates create
/mcrates givecrate <crate name>
/mcrates help
/mcrates info
/mcrates test
/mcrates reload
```

Admin testing commands remain available:

```text
/mcrates preview <crate>
/mcrates open <crate>
```

`/mcrates open` bypasses normal requirements and is intended for testing.

## Requirements

- Paper 1.21.11
- Java 21
- MiraCore 0.1.0 or newer
- MiraSpawners 0.1.3 or newer is optional and enables the native MiraSpawners reward provider

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `miracrates.admin` | OP | Editor, crate placement recovery and administration |
| `miracrates.use` | Everyone | Open placed crates |
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

## Architecture

Crate definitions, keys, rewards, opening logic and physical placements remain separate internally. The shulker item is a deployable representation of a crate definition, not a hardcoded crate class.

This keeps future GUI work such as key editors, command-reward editors, milestones and opening-style editors from requiring a redesign of the crate engine.

## Building from source

```bash
gradle clean test build
```

The build pins and SHA-256 verifies the released MiraCore 0.1.0 and MiraSpawners 0.1.3 compile-time API JARs before compilation.

Output:

```text
build/libs/MiraCrates-0.2.1.jar
```

GitHub Actions compiles and tests MiraCrates with Java 21 against Paper 1.21.11.
