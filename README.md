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

Build World Bosses directly from this branch, without first downloading or compiling a SeeU Extra JAR:

```bash
./gradlew clean build
```

The build uses a small isolated `src/seeuApi/java` source set containing **compile-time
signatures only** for the SeeU Extra Continuation 0.3.0 integration points.
These signature classes are not in the distributed World Bosses JAR or runtime
classpath; `check` verifies that they have not accidentally been packaged.
If SeeU Extra's public API changes in a future version, these signatures must
be updated to match that version. **The actual SeeU Extra Continuation mod
remains required when playing**; this change only eliminates the manual build
dependency and the former `-PseeuExtraJar` parameter.

The resulting standalone JAR is `build/libs/world-bosses-neoforge-1.21.1-0.1.0.jar`. Do not install the pre-split SeeU Extra build at the same time as Continuation. Fabric is intentionally not included for this Antarchy-specific addon.

No Antarchy source files are bundled or replaced; the mixins target Antarchy's own King/Queen renderer and entity classes.
