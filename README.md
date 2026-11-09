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
  real *nuclear* custom-nuke stage (default nuclear strength 150) once the beam
  expires. This is not the conventional 150-TNT stage.
- **Queen centre-head beam:** calls the real HBM *Balefire / antimatter* stage
  (default strength 150) at the last beam hitpoint after its firing duration.
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

**Compatibility note:** HBM and Antarchy use separate source modules with no
HBM build-time dependency here. The optional mixins are version-specific to
NTM: Continuum and Antarchy 1.21.1 and require an in-game integration test
with the installed NTM build.
