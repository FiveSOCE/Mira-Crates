# MiraCrates

MiraCrates is the GUI-first crate, key and reward engine for the Mira Minecraft plugin ecosystem. It targets **Paper 1.21.11** and **Java 21** and integrates directly with **MiraCore**.

## Download

[**Download MiraCrates v0.3.0**](https://github.com/FiveSOCE/Mira-Crates/releases/download/v0.3.1/MiraCrates-0.3.1.jar)

Current release: **v0.3.1**

## v0.3.1 physical crate removal

Administrators can remove one deployed crate instance with:

```text
/mcrates remove
```

Look directly at the placed MiraCrates shulker and run the command. The physical shulker is removed and its saved location is unregistered. The crate definition, companion key, rewards, and any other placed copies of that crate remain untouched. No crate item is returned by this command.

## v0.3.0 key and opening workflow

Keys are now mandatory for normal crate openings.

Every crate automatically receives a matching physical key when the crate definition is created. A crate is never intentionally left without a valid key.

Examples:

```text
Test Crate -> Test Key
Vote Crate -> Vote Key
Legendary Crate -> Legendary Key
```

The companion key uses a tripwire hook with hidden MiraCrates key identity data.

Existing crates from older MiraCrates versions that have no valid attached key are migrated on load. MiraCrates automatically creates and attaches a companion key for them.

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

### Crate name

Click **Crate Name**. The editor closes and asks you to type the name in chat.

That next chat message is captured as editor input and cancelled so it is not broadcast to other players. The crate editor then reopens automatically with the name applied.

### Reward items

Use normal left/right clicks in your own inventory to place the desired item on your cursor, then click **Add Reward Item**.

The reward item is copied rather than consumed.

Reward chances must total exactly **100%** before Save is available. Auto Balance divides 100% evenly between the current rewards.

When a new crate is saved:

1. MiraCrates creates the crate definition.
2. MiraCrates automatically creates its matching physical key.
3. The key is automatically attached to that crate.
4. The administrator receives the deployable crate shulker.

## Physical crate shulkers

Every crate is represented by its configured coloured shulker box.

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

## Opening modes

### Normal opening

Normal opening consumes one valid key, locks the reward before animation begins, then runs the roulette GUI.

The default roulette duration is now **120 ticks / 6 seconds**, increased from the previous 60 ticks / 3 seconds.

Existing v0.2 configuration files using the old default 60-tick duration are automatically migrated to 120 ticks on first v0.3 startup.

### Quick opening

Hold the correct key, crouch and right-click the crate.

MiraCrates consumes one key, rolls the reward through the same reward engine and grants it immediately without opening the roulette GUI.

Quick opening does not bypass:

- key requirements
- crate permissions
- cooldowns
- reward eligibility
- reward logging

It only skips the visual animation.

## Key management GUI

Open:

```text
/mcrates
```

and select **Keys**.

Each crate's automatically generated companion key appears in this menu.

On a key entry:

- **Left-click** gives yourself 1 key.
- **Right-click** gives yourself 10 keys.

This is intended to keep normal crate administration GUI-first. Legacy/admin key commands remain available for recovery and advanced use.

## Managing crates through `/mcrates`

On a crate entry:

- **Left-click** edits the crate.
- **Shift-left-click** previews its rewards.
- **Right-click** gives yourself its deployable crate shulker.

The crate list also displays how many keys are attached to each crate.

## Opening safety

The reward is selected and locked before a normal roulette animation begins.

Closing the opening GUI does not cancel a pending reward. If a player disconnects during an animated opening, MiraCrates completes the pending payout during the quit flow.

If reward delivery fails after a key has been consumed, MiraCrates refunds that key.

## Reward engine

GUI-created item rewards use direct percentages. For example:

```text
Diamond x8    50%
Spawner x1    30%
Sword x1      20%
```

produces a 50/30/20 reward split.

The underlying engine also supports:

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

Normal administration is intentionally GUI-first.

```text
/mcrates
/mcrates create
/mcrates givecrate <crate name>
/mcrates help
/mcrates info
/mcrates test
/mcrates reload
```

Advanced/admin routes remain available for recovery and integrations.

## Requirements

- Paper 1.21.11
- Java 21
- MiraCore 0.1.0 or newer
- MiraSpawners 0.1.3 or newer is optional and enables native MiraSpawners reward support

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

The build pins and SHA-256 verifies the released MiraCore 0.1.0 and MiraSpawners 0.1.3 compile-time API JARs before compilation.

Output:

```text
build/libs/MiraCrates-0.3.1.jar
```

GitHub Actions compiles and tests MiraCrates with Java 21 against Paper 1.21.11.
