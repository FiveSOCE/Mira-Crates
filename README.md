# MiraCrates

MiraCrates is the configurable crate, key and reward engine for the Mira Minecraft plugin ecosystem. It targets **Paper 1.21.11** and **Java 21** and integrates directly with **MiraCore**.

## Download

[**Download MiraCrates v0.1.0**](https://github.com/FiveSOCE/Mira-Crates/releases/download/v0.1.0/MiraCrates-0.1.0.jar)

Current release: **v0.1.0**

The design follows one important rule: crates, keys, rarities, rewards, physical locations and opening presentation are separate concepts that are linked together instead of hardcoded into individual Java classes.

## v0.1.0 foundation

The first MiraCrates checkpoint includes:

- YAML-backed crate definitions
- Physical and virtual key definitions
- Multiple accepted key types per crate
- Configurable rarities
- Two-stage weighted RNG: rarity first, reward second
- Permission-aware reward eligibility
- Item rewards captured directly from the admin's held item
- Console command rewards with `{player}` / `%player%` replacement
- Native MiraSpawners rewards through the MiraCore service registry
- MiraCrates key rewards
- XP-level rewards
- Physical crate locations linked to any world block
- Left-click reward previews
- Right-click crate openings
- Preview GUI with calculated per-player reward chances
- Roulette-style opening GUI
- Reward is selected before the animation begins
- Physical linked blocks are protected from breaking and explosions
- Persistent virtual-key balances
- Persistent crate cooldown timestamps
- Persistent per-player opening counts
- Opening history log
- Admin editor dashboard
- Runtime diagnostics
- MiraCore module registration and public MiraCrates API

## Requirements

- Paper 1.21.11
- Java 21
- MiraCore 0.1.0 or newer
- MiraSpawners 0.1.3 or newer is optional and enables the native `MIRA_SPAWNER` reward provider

## Core concepts

```text
Crate
├── Display name / icon
├── Accepted keys
├── Cooldown
├── Rewards
└── One or more linked world locations

Key
├── Physical or virtual
├── Display item
└── Can be accepted by multiple crates

Rarity
├── Display name / icon
└── Rarity weight

Reward
├── Reward type
├── Rarity
├── Weight inside that rarity
├── Display icon / name
├── Optional permission
└── Reward payload
```

A crate does not own a hardcoded chest class, key class or animation class. New crate ecosystems are created from definitions and admin commands.

## Reward probability

MiraCrates uses a two-stage weighted roll.

Example rarity weights:

```text
Common      70
Rare        25
Legendary    5
```

If `God Rune` has 20 weight inside a Legendary pool with 100 total reward weight, its final chance is:

```text
5% Legendary chance
×
20% chance inside Legendary
=
1% final chance
```

The preview GUI calculates and displays the final chance for the player viewing the crate. Permission-gated rewards are omitted from that player's eligible pool and the displayed chances are recalculated accordingly.

## Physical crate interaction

Link any block to a crate definition:

```text
/mcrates location set <crate>
```

while looking directly at the desired block.

By default:

- **Left-click** the linked block to preview rewards.
- **Right-click** the linked block to open the crate.
- Linked crate blocks cannot be broken until unlinked.
- Linked crate blocks are removed from explosion block lists.

Remove a link with:

```text
/mcrates location remove
```

## Keys

Create a physical key:

```text
/mcrates key create vote &fVote Key
```

Create a virtual key:

```text
/mcrates key createvirtual event &dEvent Key
```

Give yourself keys:

```text
/mcrates key give vote
/mcrates key give vote 10
```

Attach one or more accepted keys to a crate:

```text
/mcrates crate key vote_crate add vote
/mcrates crate key vote_crate add universal
```

Any accepted key can satisfy one opening. Virtual keys are consumed from the player's persistent balance. Physical keys are located and consumed from inventory.

If a crate has no accepted keys configured, it can be opened without a key.

## Crates

Create a crate:

```text
/mcrates crate create vote_crate &dVote Crate
```

Delete one:

```text
/mcrates crate delete vote_crate
```

Set a persistent per-player cooldown:

```text
/mcrates crate cooldown vote_crate 3600
```

That example permits one opening per hour per player.

## Rarities

MiraCrates ships with three starter rarities:

```text
common      70
rare        25
legendary    5
```

Create another:

```text
/mcrates rarity create mythic 1 &5Mythic
```

Delete one:

```text
/mcrates rarity delete mythic
```

Rewards referencing a missing rarity are not eligible to roll.

## Rewards

### Item reward

Hold the exact item you want to capture, then run:

```text
/mcrates reward item <crate> <rarity> <weight> <rewardId> [amount]
```

Example:

```text
/mcrates reward item vote_crate common 50 diamond_reward 8
```

The held item's metadata is saved into `crates.yml`; payout can therefore preserve enchantments, custom names, lore and other serializable ItemStack data.

### Command reward

```text
/mcrates reward command <crate> <rarity> <weight> <rewardId> <command...>
```

Example:

```text
/mcrates reward command vote_crate rare 20 money_10k eco give {player} 10000
```

Commands run as console. `{player}` and `%player%` are replaced with the winner's current username.

### MiraSpawners reward

When MiraSpawners is installed:

```text
/mcrates reward spawner <crate> <rarity> <weight> <rewardId> <mob> [amount]
```

Example:

```text
/mcrates reward spawner vote_crate legendary 10 zombie_spawners zombie 8
```

MiraCrates asks the registered MiraSpawners API to create legitimate spawner items rather than copying its PDC format.

### Key reward

```text
/mcrates reward key <crate> <rarity> <weight> <rewardId> <key> [amount]
```

Example:

```text
/mcrates reward key vote_crate rare 10 bonus_keys vote 3
```

### XP-level reward

```text
/mcrates reward xp <crate> <rarity> <weight> <rewardId> <levels>
```

Example:

```text
/mcrates reward xp vote_crate common 15 xp_20 20
```

### Remove a reward

```text
/mcrates reward remove <crate> <rewardId>
```

## Preview and opening

Preview a crate directly:

```text
/mcrates preview vote_crate
```

Admin test-open without consuming a key or respecting cooldown:

```text
/mcrates open vote_crate
```

Normal player openings happen through linked physical crate blocks.

The reward roll is locked before the roulette animation begins. Closing the GUI does not cancel the opening. If the player disconnects during the animation, MiraCrates finishes the pending payout during the quit event rather than silently losing the selected reward.

If a selected reward provider fails to deliver, the consumed key is refunded where applicable and the failure is logged.

## Editor dashboard

Run:

```text
/mcrates
```

The v0.1.0 dashboard provides browsable views for:

- crates
- keys
- rarities
- crate reward previews

Definition mutations are currently performed through the in-game `/mcrates` commands so every change remains explicit and easy to diagnose. The backend is intentionally structured so GUI-driven mutation/chat prompts can be layered on without changing the crate model.

## Diagnostics

```text
/mcrates info
/mcrates test
/mcrates reload
```

`/mcrates test` verifies the MiraCore API/module registration, definitions, rarity data, location persistence, reward engine and data files.

## Command reference

```text
/mcrates
/mcrates help
/mcrates info
/mcrates test
/mcrates reload

/mcrates crate create <id> [display name]
/mcrates crate delete <id>
/mcrates crate cooldown <crate> <seconds>
/mcrates crate key <crate> <add|remove> <key>

/mcrates key create <id> [display name]
/mcrates key createvirtual <id> [display name]
/mcrates key delete <id>
/mcrates key give <key> [amount]

/mcrates rarity create <id> <weight> [display name]
/mcrates rarity delete <id>

/mcrates reward item <crate> <rarity> <weight> <id> [amount]
/mcrates reward command <crate> <rarity> <weight> <id> <command...>
/mcrates reward spawner <crate> <rarity> <weight> <id> <mob> [amount]
/mcrates reward key <crate> <rarity> <weight> <id> <key> [amount]
/mcrates reward xp <crate> <rarity> <weight> <id> <levels>
/mcrates reward remove <crate> <id>

/mcrates location set <crate>
/mcrates location remove
/mcrates preview <crate>
/mcrates open <crate>
```

Aliases:

```text
/miracrates
/mcrates
/mcrate
```

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `miracrates.admin` | OP | Editor and administration commands |
| `miracrates.use` | Everyone | Open linked physical crates |
| `miracrates.preview` | Everyone | Preview linked physical crates |

Rewards may also declare their own permission requirement directly in `crates.yml`.

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

`opening-history.log` is created when the first successful opening is recorded if history logging is enabled.

## Public API

MiraCrates registers `MiraCratesApi` through MiraCore's shared service registry.

```java
MiraCratesApi crates = core.services()
        .get(MiraCratesApi.class)
        .orElseThrow();

crates.giveKey(player, "vote", 5);
crates.openCrate(player, "vote_crate", false);
```

The API also exposes crate/key IDs, crate snapshots and physical key item creation.

## Building from source

```bash
gradle clean test build
```

The build pins and SHA-256 verifies the released MiraCore 0.1.0 and MiraSpawners 0.1.3 compile-time API JARs before compilation.

Output:

```text
build/libs/MiraCrates-0.1.0.jar
```

GitHub Actions compiles and tests MiraCrates with Java 21 against Paper 1.21.11.
