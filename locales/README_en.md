<div align="center">

| [中文](../README.md) | [English](README_en.md) | [日本語](README_ja.md) |
|:------------------:|:-----------------------:|:-------------------:|

<img src="../assets/logo.png" alt="Sakura SignIn" width="320" />

# Sakura SignIn

**A sign-in reward mod for Minecraft Forge, Fabric, and NeoForge.**

</div>

---

## Table of Contents

- [Sakura SignIn](#sakura-signin)
    - [Table of Contents](#table-of-contents)
    - [Meaning](#meaning)
    - [Introduction](#introduction)
    - [Features](#features)
    - [Configuration](#configuration)
    - [Commands](#commands)
    - [Reward System](#reward-system)
    - [Data and Migration](#data-and-migration)
    - [Building](#building)
    - [License](#license)

## Meaning

- **Sakura (樱花)**: A symbol of happiness, hope, and the pursuit of a brighter future.
- **Sign-In (签)**: A daily sign-in.
- **Sakura SignIn (樱花签)**: Every sign-in records another cherished day of persistence and effort.

## Introduction

This project is for Minecraft Forge, Fabric, and NeoForge servers, providing daily sign-ins, make-up sign-ins, and
configurable rewards.
The mod is required on the server and optional on the client. Players without the client mod can still sign in, claim
rewards, draw from lottery pools, and configure personal dates through commands.
Installing the client mod adds a sign-in calendar, reward configuration editor, lottery screen, personal date settings,
and an inventory quick entry.

This project depends on [Banira Codex](https://github.com/VanillaXin/BaniraCodex_MC), which provides shared
configuration, notifications, screens, localization, permissions, and cross-loader adaptation.

## Features

- **Sign-ins and make-up sign-ins**: Daily sign-ins, make-up cards, batch make-up sign-ins, automatic reward claiming,
  and a configurable make-up range.
- **Layered reward rules**: Base, continuous, cycle, yearly, monthly, weekly, date-time, cumulative, random pool, CDK,
  personal date, and lottery pool rewards.
- **Extensible reward types**: Built-in items, effects, experience points, experience levels, make-up cards,
  advancements, messages, and server commands, with registration support for other mods.
- **Visual reward editing**: Rule-based reward groups with multi-selection, dragging, copy and paste, undo and redo,
  duplicate merging, and permission control.
- **Personal date rewards**: Server-defined presets with player-selected calendars and dates, yearly or monthly
  recurrence, sign-in or online delivery, and configurable claim windows.
- **Active lottery draws**: Multiple pools, single and batch draws, daily/weekly/monthly limits, cooldowns, preview
  permissions, and multiple client reveal animations.
- **Flexible sign-in day boundary**: A fixed daily reset time or an interval-based next sign-in time.
- **Online-time requirements**: Separate lifetime and current-day online-time requirements before signing in.
- **Poor Translation**: Text descriptions might be ambiguous or unclear (not just in English).
- **Poor Code**: Bad code + negligent testing = a bunch of smelly bugs.

## TODO

- [ ] **Reward claim conditions**: Allow rewards to require levels, experience, items, continuous sign-in days, custom
  levels from other mods, or virtual currencies

---

## Configuration

Configuration can be changed through the Sakura SignIn editor or by editing the files below. Refer to in-game tooltips
and generated comments for each option's meaning and valid range.

### Shared Files

- Calendar rules [`config/sakura_sign_in/calendar-rules.json`](/config/calendar-rules.json)
- Reward configuration [`config/sakura_sign_in/reward_option_data.json`](/config/reward_option_data.json)
- Reward edit history `config/sakura_sign_in/history/`
- Reward configuration backups `config/sakura_sign_in/backups/reward-config/`
- Sakura SignIn world data `world/vanilla.xin/sakura_sign_in/`
    - Monthly player sign-in details `history/<UUID>/<year-month>.nbt`
- Legacy player-data migration backups `world/vanilla.xin/backups/sakura_sign_in/`
- Vanilla Xin Series Common Config: `config/vanilla.xin/common_config.json` (stores shared defaults for language, help
  pagination, and virtual permissions)
- Vanilla Xin Series Player Data: `world/vanilla.xin/playerdata/*.nbt` (stores sign-in totals, make-up cards, CDK
  records, personal dates, and lottery state)

### Mod Files

- Common and server behavior [`config/sakura_sign_in-common.toml`](/config/sakura_sign_in-common.toml)
- Client settings [`config/sakura_sign_in-client.toml`](/config/sakura_sign_in-client.toml)

---

## Commands

The default root command is `/sakura`. Command names, concise commands without the root prefix, and related permissions
can all be changed in the configuration.

- **help**: Show paginated command help.
  **Parameters**:
    1. `[<page>]`
- **sign**: Sign in or make up a missed day. Without a date, it signs in for the current day; `all` uses available
  make-up cards on unsigned days within the allowed range.
  **Parameters**:
    1. `[<year> <month> <day>]`
    2. `all`
- **reward**: Claim the reward for a signed date. Without a date, it claims the current day; `all` claims every
  unclaimed sign-in reward.
  **Parameters**:
    1. `[<year> <month> <day>]`
    2. `all`
- **signex**: Sign in and immediately claim the corresponding reward.
  **Parameters**:
    1. `[<year> <month> <day>]`
    2. `all`
- **cdk**: Redeem a code for its rewards.
  **Parameters**:
    1. `<code>`
- **card**: Show your make-up card count or let an administrator manage player cards.
  **Parameters**:
    1. `give <amount> [<player>]`: Add make-up cards
    2. `set <amount> [<player>]`: Set the make-up card count
    3. `get <player>`: Show another player's make-up card count
- **lottery**: List available pools or perform a draw.
  **Parameters**:
    1. `list`
    2. `draw <pool ID> [<count>|all]`
- **language**: Select the player's language source or a specific language.
  **Parameters**:
    1. `<client|server|language code>`
- **config**: Change common server configuration or the player's own personal dates.
  **Parameters**:
    1. `common <config key> <config value>`
    2. `player personalDate [list]`
    3. `player personalDate set <preset ID> <slot> <calendar ID> <date>`
    4. `player personalDate clear <preset ID> <slot>`

Date arguments accept absolute values and `~`-relative forms, such as `2026 8 23`, `~ ~ ~`, and `~ ~ ~-1`. Concise
commands such as `/sign`, `/reward`, `/signex`, and `/cdk` are also registered by default and can be disabled in the
server configuration.

---

## Reward System

### Reward Rules

| Rule                      | Description                                                                                 |
|:--------------------------|:--------------------------------------------------------------------------------------------|
| Base reward               | Granted on every sign-in                                                                    |
| Continuous sign-in reward | Granted at specified continuous-day milestones, with optional lower-tier repetition         |
| Cycle sign-in reward      | Matched by continuous sign-in day and optionally restarted after the largest configured day |
| Yearly sign-in reward     | Granted when signing in on a specified date each year                                       |
| Monthly sign-in reward    | Granted when signing in on a specified date each month                                      |
| Weekly sign-in reward     | Granted when signing in on a specified weekday                                              |
| Date-time reward          | Granted for a specified date, time, or range, with support for ignored date components      |
| Cumulative sign-in reward | Granted when total sign-in days reach a specified value                                     |
| Random reward pool        | Selects a reward group by probability on each sign-in                                       |
| CDK reward                | Claimed with a redemption code and optionally limited by count and expiration               |
| Personal date reward      | Granted according to a server preset and a player-selected calendar date                    |
| Lottery pool              | Drawn actively by the player and limited by counts, periods, and cooldowns                  |

### Reward Types

Built-in reward types include:

- Item stacks
- Effects
- Experience points
- Experience levels
- Make-up cards
- Advancements
- Messages
- Server commands

Every reward can have its own probability, and the server can decide whether player luck affects that probability.
Reward rules and reward types have separate permission levels and Banira virtual permissions. Clients without permission
do not receive detailed configuration for restricted rules.

Other mods can register custom reward types through `xin.vanilla.sakura.api.reward`, supplying codec, validation, grant,
description, merge, and optional client editing and presentation behavior without changing Sakura SignIn's built-in
distribution logic.

### Calendars and Personal Dates

Sakura SignIn includes the Gregorian calendar and built-in Chinese lunar-calendar rules for 1900 through 2100. An
editable `calendar-rules.json` is generated on first launch. Servers can supplement or replace calendar rules in this
file, and successfully loaded calendars automatically become available in personal date settings.

Server-defined personal date presets can limit the number of dates, allowed calendars, yearly or monthly recurrence,
sign-in-only or online delivery, and the valid claim window before and after the target date. Each reward is granted
successfully only once and cannot be reclaimed after expiration.

### Lottery Pools

Each pool can independently configure daily, weekly, and monthly draw counts, cooldown time, and preview visibility.
Players with the client mod can choose a pool, inspect any public reward details, and select a reveal animation. The
server always determines the result, and the reward is confirmed and delivered only after the animation finishes or is
skipped.

---

## Data and Migration

- Banira player data stores summaries such as cumulative and continuous sign-in days, make-up cards, CDK records, and
  personal dates
- Sign-in details are split by player and month; deleting old monthly details does not affect cumulative days,
  continuous days, or later reward calculations
- `history.retentionMonths` controls how many recent months of details are retained; `0` keeps them permanently
- `history.retentionPolicy` either deletes an entire expired month file or removes only its larger reward snapshots
- When legacy player data is found, the mod writes and verifies the new format and backup before removing old
  Capability, Attachment, or storage nodes; failed migrations keep the old data for a later retry
- Legacy reward configuration is converted to the current reward structure when read and uses the new format after
  saving

Back up the entire world directory before migrating a save. Do not manually remove or move player-data files while the
server is running.

---

## Building

Minecraft versions and loaders are maintained in separate `forge/*`, `fabric/*`, and `neoforge/*` branches. The docs
branch provides one batch entry for all maintained branches:

```bat
scripts\build-all.bat
```

By default, the script builds every local loader branch and excludes other namespaces such as `dev/*` and
`maintenance/*`. Each branch is built in a detached temporary worktree without switching the current checkout.

List selected branches and validate JDK discovery without running a build:

```bat
scripts\build-all.bat -ListOnly
```

Select branches with glob expressions:

```bat
scripts\build-all.bat -BranchExpression "forge/*"
scripts\build-all.bat -BranchExpression "*/21.1"
scripts\build-all.bat -BranchExpression "forge/*,!forge/16.5"
scripts\build-all.bat -BranchExpression "fabric/18.2"
```

Expressions beginning with `!` exclude matching branches. The previous parameter name `-Branches` remains available as
an alias.

After switching to a single target branch, it can also be built directly:

```bat
gradlew.bat clean test assemble
```

Artifacts are collected under `builds/<mod version>/` in the docs worktree. Regular artifacts require Banira Codex to be
installed separately; artifacts with `-all` in the filename include the matching Banira Codex version.

Minecraft 1.16.5, 1.18.2, 1.19.2, 1.20.1, and 1.21.1 are currently maintained. NeoForge support starts at 1.21.1. Legacy
branches under `maintenance/*` are excluded from routine maintenance.

---

## License

**MIT License**

If you have any questions or suggestions, feel free to submit Issues or Pull requests.
