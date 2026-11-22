# Spawn Notification Client

A **client-only Fabric mod** for **Minecraft 1.21.1 + Cobblemon 1.8.4** that alerts you when a shiny, legendary,
mythical, Ultra Beast or Paradox Pokémon (or any species you choose) loads near you.

It works on any multiplayer server. **Nothing is installed on the server, and the mod sends no packets.**
It only reads entity data the server already sends to every client that can see the Pokémon.

> This is an independent re-implementation of the *idea* behind
> [Spawn Notification](https://github.com/timinc-cobble/cobblemon-spawn-notification) (MIT), not a port.
> The original is a server-side mod; see "How it differs" below.

## Build status — read this first

| Check | Status |
|---|---|
| Core logic (config, filters, dedupe, cooldowns) compiled and tested | **Done: 40 checks pass** (`bash selftest/run.sh`) |
| All 15 source files parse with no syntax errors | **Done** |
| Every unresolved symbol in a Minecraft-less javac run is an external API name, none are my own classes | **Done** |
| `./gradlew build` against real Minecraft / Fabric API / Cobblemon | **Not run.** The authoring sandbox cannot reach Mojang, Fabric, Cobblemon or Maven Central. |
| In-game test on a real server | **Not run** |

So there is **no verified JAR** in this package. Build it yourself in one command (below), or push the repo to
GitHub: `.github/workflows/build.yml` builds it on GitHub's runners and attaches the JAR as a downloadable artifact.

Cobblemon calls were limited to ones that appear in other published Fabric 1.21.1 Cobblemon mods or in the
original Spawn Notification source (`getPokemon`, `getSpecies`, `getResourceIdentifier`, `getName`,
`getTranslatedName`, `getForm().getLabels()`, `getShiny`). Plugin versions in `gradle.properties` were copied from a
working Fabric 1.21.1 + Cobblemon client mod. If Gradle cannot resolve one, check https://fabricmc.net/develop.
If a compile error appears, it will be in the glue under `client/`, and it will be one line.

## Install

1. Fabric Loader ≥ 0.16, Minecraft **1.21.1**, Java 21.
2. In your `mods` folder you need: **Fabric API**, **Cobblemon 1.8.4** (and the Fabric Language Kotlin mod Cobblemon already requires).
3. Drop `spawn-notification-client-1.0.0.jar` into `mods`. Optional: Mod Menu adds a Settings button.
4. Launch. The config file `config/spawnnotifyclient.json` is created on first start.

Do **not** put it on the server. It is marked `"environment": "client"`.

## Build

```bash
# Linux / macOS                      # Windows
./gradlew build                      gradlew.bat build
```
Output: `build/libs/spawn-notification-client-1.0.0.jar` (use that one, not the `-sources` jar). Requires JDK 21.

Quick logic test with no Minecraft needed: `bash selftest/run.sh`.

## Using it

* **Settings screen:** Mod Menu → Spawn Notification Client → Configure, the `/spawnnotify` command, or the
  "Open Spawn Notification settings" key (unbound by default; set it under Controls).
* **Commands** (client-side, never sent to the server):
  `/spawnnotify` (settings) · `/spawnnotify toggle` · `/spawnnotify reload` · `/spawnnotify test [shiny|legendary|mythical|ultra_beast|paradox|custom]`

## What it does

| Requirement | How |
|---|---|
| Detect Pokémon visible/loaded client-side | Fabric `ClientEntityEvents.ENTITY_LOAD` / `ENTITY_UNLOAD`, filtered to Cobblemon `PokemonEntity`. **No world scanning.** |
| Species | `pokemon.getSpecies()` |
| Shiny | `pokemon.getShiny()` |
| Legendary / Mythical / Ultra Beast / Paradox | Cobblemon form labels `legendary`, `mythical`, `ultra_beast`, `paradox` (same labels the original uses) |
| Species filters | `speciesWhitelist` (always alert), `speciesBlacklist` (never alert, wins over shiny/legendary). Names ignore case, spaces, punctuation and `cobblemon:`, so `Mr. Mime`, `mr_mime` all work. |
| Category filters | One toggle per category, plus `extraLabels` for any other Cobblemon label |
| On-screen notification | Stacked banners, configurable duration, fade in/out |
| Coloured chat | Per-category colours (shiny gold, legendary purple, mythical aqua, Ultra Beast red, Paradox green) |
| Sound | Per-category sound event id (any vanilla/Cobblemon/resource-pack sound), master toggle, volume, pitch |
| No duplicate alerts | Per-entity UUID memory (survives chunk unload/reload for `entityMemorySeconds`, default 15 min) |
| Cooldowns | Per-species cooldown; global sound cooldown (alert still shows, sound skipped); shiny can bypass both |
| Reconnects / dimension change / unload | Join and disconnect clear all state; a new `ClientLevel` drops the pending queue; unload removes queued entities |
| Performance | Event-driven. Idle cost is one empty-map check per tick. At most 16 queued entities are read per tick. |

### Why there is a short delay before an alert
Cobblemon syncs species and shiny state to the client *just after* the entity is added. Reading it in the load event could
return placeholder data and cause false alerts. New entities wait `settleTicks` (default 10 = 0.5 s) and are read once.
If you ever see a wrong species in an alert, raise `settleTicks`.

## Config reference (`config/spawnnotifyclient.json`)

See [`default-config/spawnnotifyclient.json`](default-config/spawnnotifyclient.json). Missing keys use defaults; out-of-range values are clamped;
a corrupt file is renamed to `.broken` and replaced with defaults.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | true | Master switch |
| `alertShiny/Legendary/Mythical/UltraBeast/Paradox` | true | Category toggles |
| `ignoreOwned` | true | Skip Pokémon with an owner (other players' Pokémon) |
| `speciesWhitelist` / `speciesBlacklist` / `extraLabels` | empty | Custom lists |
| `visualAlerts`, `chatAlerts` | true | Output channels |
| `includeCoordinates`, `includeDistance` | true | Location text |
| `displayDurationSeconds` | 6.0 | Banner time (1–60) |
| `maxVisibleBanners`, `hudOffsetY` | 3, 12 | Banner stack |
| `soundEnabled`, `volume`, `pitch` | true, 1.0, 1.0 | Volume 0–1, pitch 0.5–2 |
| `sounds` | see file | Category → sound id; `""` silences one category |
| `perSpeciesCooldownSeconds` | 30 | Same species won't re-alert within this window (0 = off) |
| `globalSoundCooldownSeconds` | 2 | Min gap between sounds (0 = off) |
| `shinyBypassesCooldown` | true | Shiny ignores both cooldowns |
| `entityMemorySeconds` | 900 | How long an alerted entity is remembered |
| `maxDistance` | 0 | Ignore Pokémon farther than this (0 = no limit) |
| `settleTicks` | 10 | Wait before reading a new entity (see above) |

## How it differs from the original, and limitations

The original runs **on the server** and broadcasts to nearby players, so it can announce things a client can never know.
A client-only mod cannot reproduce those, so they are intentionally **not** included:

* **Rarity buckets (`ultra-rare`, etc.)**: the original reads a bucket tag from server-side `persistentData`. It is not synced to clients.
* **Alpha / Max-IV tags**: also server-side data.
* **Event messages** such as captured, fainted, died, hatched, fished up, resurrected, despawned: these are server events with no client-side signal.
* **Range**: you are alerted only for Pokémon your client has loaded (limited by the server's entity tracking/view distance). Spawns farther away are invisible to any client mod.
* **Alerts on join**: Pokémon already near you when you log in are alerted once as they load.
* **Unverified at runtime**: sync timing, and that `getOwnerUUID()` is populated client-side for owned Pokémon (if not, `ignoreOwned` just won't filter them). Cobblemon could also change these internals in a later release.
* Only English text is provided. Translations are a lang-file addition.

Uses only data the server already sends to your client. Some servers restrict "radar"-style mods in their rules, so check yours.

## Licence and attribution
MIT (see `LICENSE`). The original Spawn Notification (MIT, © Timothy Metcalfe) was used as a *functional reference* (feature set, label names, the
Cobblemon shiny-chime sound id). No code was copied. Mod Menu, Fabric API and Cobblemon are compile-time dependencies and are not bundled.
