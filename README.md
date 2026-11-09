# World Bosses — SeeU Extra Continuation addon (1.21.1 NeoForge)

**Separate installable mod ID: `world_bosses`.** This is not another copy of SeeU Extra. Install both `world_bosses` and the `seeu_extra` JAR built from the sibling [seeu-extra-continuation](../seeu-extra-continuation) branch, along with Antarchy 2.3.x, SeeU and NeoForge.

## Behaviour

Both Antarchy royals (`antarchy:king` and `antarchy:queen`) have separate, server-owned off-chunk persistence. Each boss is serialized with its original NBT, health and UUID, moves with simple low-cost directional flight in unloaded areas, detects survival players out to 128 chunks, and only restores Antarchy's **real** entity when nearby chunks are naturally entity-ticking. No chunk tickets, vanilla noise mixins or Voxy renderer mixins are used.

- **King:** continuous world health bar, native-directional roaming, distant player-state decrees with shared combat cooldown, attack/beam animation synchronization and Antarchy's original combat/decree behaviours after materializing.
- **Queen:** independent persistent roaming and purple world health bar, persistent GeckoLib/beam state, and native Antarchy combat after materializing, preserving Queen's unique phases, chronosphere, gravity stomp, black hole, acceleration, debris cyclone and other attacks. Her abilities do **not** execute in offloaded chunks.
- Both bosses use SeeU Extra's F8 distance opt-ins and shared shader/LOD-depth correction automatically. Turn the bosses ON in F8 to make them render at distances. The World Bosses mod registers the native-visual adapter independently of the F8 animation toggle.
- Existing worlds' offloaded King SavedData continues using `seeu_extra_persistent_king`. Queen uses `world_bosses_persistent_queen`. **Back up existing worlds** before testing migration and the new Queen behavior.

The config `config/world-bosses-server.json` controls enabling each boss, roaming, the global boss bars and the King's distant decrees independently from SeeU Extra's own allow/deny lists. Queen's **native** global-roam override is off by default to avoid interrupting her distinctive grounded and scripted attacks; her chunk-independent offloaded flight is still enabled. The separate stuck-flight fallback can be changed in this config.

## Build (Java 21)

Build World Bosses directly with Java 21 and Git installed:

```bash
./gradlew clean build
```

There is **no local SeeU Extra JAR and no API-stub source set**. Gradle's
`sourceControl` in `settings.gradle` automatically checks out the
`seeu-extra-continuation` branch of this repository and resolves real
`:neoforge` and `:common` sources as **compile-only** dependencies.
On the first build this requires Internet access and may take longer while
SeeU Extra and NeoForge are compiled; subsequent Gradle builds can reuse caches.
The bundled addon still only contains World Bosses classes (`check` verifies
this), and the real `seeu_extra` mod is **required separately at runtime**.

If you later publish SeeU Extra Continuation as a Maven artifact, the source
dependency can be replaced by a normal versioned `compileOnly` dependency.

The resulting standalone JAR is `build/libs/world-bosses-neoforge-1.21.1-0.1.0.jar`. Do not install the pre-split SeeU Extra build at the same time as Continuation. Fabric is intentionally not included for this Antarchy-specific addon.

No Antarchy source files are bundled or replaced; the mixins target Antarchy's own King/Queen renderer and entity classes.


## Optional NTM: Continuum / HBM abilities

When `hbm` is installed, World Bosses hooks Antarchy's existing attack
scheduling without changing Antarchy, NTM, or Neo-Voxy source files.

- **King centre-head beam:** fires normally with locked aim, then calls HBM's
  real *nuclear* custom-nuke stage (default nuclear crater radius 30) once the beam
  expires. This is not the conventional 150-TNT stage.
- **Queen centre-head beam:** calls the real HBM *Balefire / antimatter* stage
  (default Balefire crater radius 30) at the last beam hitpoint after its firing duration.
- **Queen black hole:** keeps Antarchy's native telegraph, roar and particles;
  replaces the thrown hole with HBM's field at the already selected impact
  destination, grows to size 6.4 (96-block pull radius), and expires in 100
  ticks by default. HBM still applies its configured dimension restrictions.
- **Immunities:** both royals are marked `hbm:radiation_immune` and excluded
  from HBM nuclear damage/knockback/fire and HBM black-hole pull/damage. Royal
  bolts, elemental projectiles, ice spikes and other Antarchy projectiles,
  Queen Manticores, and royal black-hole entities are exempt from the Queen's
  HBM black-hole pull. Queen Manticores **are not immune to nuclear or
  Balefire explosions**.
- **Fallback:** without HBM, Antarchy's normal Queen black hole still works
  and nuclear beam effects are disabled. HBM remains an **optional dependency**.

Settings in `config/world-bosses-server.json`: `hbmAbilities`,
`kingNuclearStrength`, `queenBalefireStrength`,
`queenBlackHoleMaxSize`, `queenBlackHoleLifetimeTicks` and
`royalHbmImmunity`. These explosions can destroy a very large area; test
in a backed-up world. Existing Voxy snapshot/memory limits are unchanged.

Both beam explosions default to HBM crater strength 30, configurable separately
in `world-bosses-server.json`. Previous default pairs (16 King / 12 Queen)
and legacy 150-strength settings migrate to 30 automatically. Configured
strengths are permitted from 1 to 100, with a final safety clamp applied
at detonation. The Queen's black-hole uptime is separately configurable
using `queenBlackHoleLifetimeTicks`, default 100 (five seconds); its size
remains configurable without changes. These values control HBM's actual
terrain modification radius, not just particle size or cooldown.

**Compatibility note:** HBM and Antarchy use separate source modules with no
HBM build-time dependency here. The optional mixins are version-specific to
NTM: Continuum and Antarchy 1.21.1 and require an in-game integration test
with the installed NTM build.


## Persistent pursuit and King's Treason

In `config/world-bosses-server.json`, `distantPursuitRangeChunks`
controls how far **both** offloaded royals identify and chase players:
default **64 chunks** (1024 blocks), minimum **0** (no distant pursuit,
roaming continues), maximum **512** (8192 blocks). This does not affect
server simulation / hitbox restoration, native combat, or the separately
configured distant Decree and ambient beam strike ranges.

- Native/offloaded transitions now retain the current target UUID whenever it is
  still targetable, avoiding unnecessary re-targeting just because a nearby
  chunk became entity-ticking. No extra chunk tickets or distant pathfinding.
- Persistent flight remains a **logical server-side stand-in** outside entity
  ticking chunks; its steering is independent of loaded blocks. Native Antarchy
  AI remains responsible for physical combat/landing and action-specific motion.
  The complete no-mode-switch movement-director refactor is **not** implemented
  by this target-handoff change alone.
- A successful *Decree violation* awards the King exactly one **Treason point**.
  Antarchy's native `violateDecree` path credits only the first violation of
  that proclamation per participant. The four offloaded player-state decrees
  credit the same point through World Bosses' persistent flight state.
- Treason persists across native/offloaded transitions and ordinary saves.
  The server-only `RoyalTreason.points/award/trySpend` API is ready for
  special attacks; **no points are spent and no grabs trigger yet** until the
  corresponding King animations and model anchors exist.

### Animating a future Treason grab

Antarchy already uses GeckoLib's `GeoBone.setTrackingMatrices(true)` and
`getWorldPosition()` to attach royal beam endpoints smoothly to moving bones.
The same renderer-side technique can anchor a player's *visual model* to a
new King bone such as `grab_anchor_right`. Parent that anchor to the moving
hand/palm in the King geo model, then animate the hand with named attack
sequences (for example `treason_grab`, `treason_slam`, `treason_throw`).
The client interpolates the anchored model every rendered frame while the
server retains authority over the player's collision box, damage, camera
behavior, and guaranteed release on interruption. A player should never be
teleported to raw 20-TPS bone positions to accomplish visual attachment.


## Authoritative royal combat stats and offloaded attacks

World Bosses now owns a dedicated `config/world-bosses-royal-stats.json`
containing the complete 72-setting Antarchy `[royalBosses]` profile:
health, damage, ranges, bite/beam timings, Decree and royal-muster cooldowns,
King elemental cooldowns, Queen black-hole settings, multiplayer scaling,
and Glimmer multipliers. On the **first** launch the addon copies the
`[royalBosses]` values from `config/antarchy_mobs.toml`, when present.
From then on, edit **only** `world-bosses-royal-stats.json` for royal stats.
Antarchy's original TOML is not modified; AntarchySettings getter hooks
make World Bosses authoritative even if the original config is reloaded.
Restart the game/server after changing the World Bosses stat file. HBM
nuclear/Balefire strength and black-hole lifetime remain in the separate
`world-bosses-server.json`.

**Persistence:** Offloading King or Queen now snapshots both the Antarchy
RoyalAttackScheduler cooldown map and private per-boss integer cooldown
counters. Cooldowns continue ticking while the boss exists only as its
dimension-wide saved flight state and are restored on native activation.
This prevents the attack timers from resetting at the tracking boundary.

**In-flight beam:** If a centre-head HBM nuclear/Balefire beam has already
started when the boss offloads, the addon stores its final impact and
remaining firing ticks as a dimension SavedData event, which still detonates
at the scheduled time regardless of whether the native boss is loaded.
This preserves the pending blast through reactivation and world reload.
This is a *partial* attack continuation: remote beam geometry, per-tick
beam damage, active animation callbacks, and other active scripted actions
are not yet simulated independently of the native entity. Extending the
single persistent flight/attack director will be needed for full parity
without switching states. No chunk tickets or Voxy queue changes are added.

Treason grab animations remain a separate future feature. Attach a
`grab_anchor_right` model bone to the King's grabbing hand and provide
the updated model and animation files before implementing bone-followed
player visuals and server-authoritative grabs.


## Distant Godzilla-style ground beam strikes

When NTM: Continuum is available, King and Queen may occasionally fire a
centre-head beam *at the ground while offloaded beyond normal entity ticking*.
This is an ambient strike, not an extra normal-range combat attack.

The server selects one random ground point ahead/sideways of the flying boss,
using a loaded chunk's heightmap when available, or a single chunk-generator
height estimate otherwise. **Target selection never requests/generates chunks.**
An approximate height may miss player-built or structure-modified terrain;
the HBM crater itself can still update/load chunks. Candidate points too high
for a downward beam or too close to players are rejected.

World Bosses uses a tiny NeoForge client packet to show the centre-head beam
through Antarchy's existing SeeU proxy renderer (no extra shader, Voxy or
Minecraft mixin). The 45-tick default beam remains on screen before its
normal HBM explosion; HBM/Voxy nuke sync handles distant mushroom clouds and
updated terrain. Strikes are persisted as a SavedData deadline and complete
even when the royal returns to native simulation or the server restarts.
Only one outstanding beam event per boss is allowed.

Config in `world-bosses-server.json`:

- `distantGroundBeamStrikes`: true by default; disable all ambient ground shots
- `kingDistantBeamCooldownTicks`, `queenDistantBeamCooldownTicks`: 3600
  by default (plus a random 0–50% delay, approximately 3–4½ minutes)
- `distantGroundBeamDurationTicks`: 45 by default
- `distantGroundBeamMinimumRange` and `distantGroundBeamMaximumRange`:
  48–160 blocks from the boss by default
- `distantGroundBeamMinimumPlayerDistance`: 48 blocks
- `distantGroundBeamViewerRange`: 2048 blocks, plus 256 visual slack

The existing `kingNuclearStrength` / `queenBalefireStrength` values (30 by
default) apply to these explosions too. The normal Antarchy centre-beam
cooldown is also respected in persistent flight and is credited when a random
strike fires. No Voxy chunk queue or RAM defaults are changed. No distant
beam damage ticks or entity-hitbox simulation are introduced.
