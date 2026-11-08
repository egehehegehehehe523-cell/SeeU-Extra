# SeeU Extra (Minecraft 1.21.1)

This fork renders **opted-in non-player entities** beyond Minecraft's normal render/tracking range using [SeeU](https://github.com/cat4blep/SeeU). It supports Fabric and NeoForge, with no Neo-Voxy or Minecraft mixins. NeoForge has an **optional Antarchy King-only** idle-flight test mixin.

## In-game configuration

**Press F8** (rebindable under Controls → Key Binds → SeeU Extra) to open the searchable list of every entity type registered on the **client**.

- All entity types start **OFF**. Only entities explicitly switched ON generate distant snapshots for that client.
- Each enabled type has its own maximum distance in **blocks**; the default is 8192. Type a distance from 1 through the configured client global maximum.
- Search by display name or registry ID, e.g. `antarchy:king` or `antarchy:queen`. Changes are saved when closing the screen and sent to the server without reconnecting.
- The client writes `config/seeu-extra-client.json`. Enabled types are stored in `entityDistances`; omitted types remain disabled. The global `maximumDistanceBlocks` and `minimumDistanceBlocks` remain safety limits.

## Server permissions and limits

The server writes `config/seeu-extra-server.json`. **New installs** default to `mode: "ALL"`, but the client opt-in list is empty, so distant entity snapshots are never sent until a player enables types. Existing server configs are preserved, including `DISABLED` or `SELECTED`; edit them if needed.

- `SELECTED` permits `types` and `namespaces`. An enabled client entry must also be permitted by the server.
- `ALL` permits the player's opted-in types; the client cannot override `excludedTypes` or `excludedNamespaces`.
- The lower of server, client-global and client-per-type maximum distances wins. The higher minimum wins.
- `entityCap` and `updateIntervalTicks` bound server traffic and snapshot work.
- `persistentKingSimulation: true` (default) enables **one persistent, chunk-independent King simulation per dimension**. A discovered King outside nearby eligible players' simulation range is serialized as full entity NBT (including health and Antarchy-specific fields), discarded from its original chunk, and replaced by a lightweight server-authoritative state in dimension SavedData. That state wanders at **0.85 blocks/tick** (17 blocks/sec); every 20 ticks it tests online survival/adventure players within **128 chunks (2048 horizontal blocks)** and pursues the nearest at **1.20 blocks/tick** (24 blocks/sec). Movement uses only player coordinates and the King's heading; there are no collision/terrain scans, distant chunk tickets, or chunk generation. The original entity/UUID is restored only when a player naturally makes the simulated position entity-ticking, with a two-chunk activation buffer. It continues moving even if nobody enables King LOD rendering.
- `liveKingTicking: true` is now a **legacy fallback** used only when `persistentKingSimulation: false`. It keeps an observed King alive with temporary chunk tickets, which can load/generate terrain. Disable persistent simulation only after recovering your King in active mode; otherwise its saved distant state cannot be materialized by the disabled manager.
- `kingIdleWanderFallback: true` (NeoForge only) is the Antarchy King-only real-entity stuck-flight fallback. Its cruise speed is now **0.85 blocks/tick**, and movement follows the body's actual yaw so the boss does not visually fly backwards while turning. Its normal Antarchy combat AI still takes priority. `kingGlobalRoam` is still an opt-in real-entity experiment; the persistent distant flight above does not require it.
- The persistent King needs to be **observed loaded at least once** to migrate an existing worldboss into SavedData. A King already unloaded before installing this update will not be discovered until his old chunk is loaded normally. Back up worlds before testing experimental entity serialization.
- Players and anything carrying a player stay on SeeU's existing player path, not SeeU Extra's proxy path.
- Server and client must run the **same fork version** (protocol 3). A mismatched upstream SeeU Extra build will not connect.

### Example client config

```json
{
  "configVersion": 2,
  "enabled": true,
  "maximumDistanceBlocks": 8192,
  "minimumDistanceBlocks": 0,
  "entityDistances": {
    "antarchy:king": 4096,
    "antarchy:queen": 4096
  }
}
```

## Renderer behavior and limitations

- When a real client entity exists **inside normal chunk range** but vanilla's entity-distance test rejects it, SeeU Extra renders that real entity and preserves its GeckoLib controller and synced boss state. **Beyond normal chunks**, it continues to use a synthetic, interpolated proxy even if a non-ticking client entity happens to remain loaded. The proxy's GeckoLib controller is separate from a newly spawned real entity's, so an initial animation phase reset can still occur after a complete unload/retrack. This avoids reintroducing the frozen distant animation bug.
- Animated model visibility is tested using expanded bounds so wings and large geometry are not culled at the collision-box edge.
- Proxies use normal registered mod entity renderers, including GeckoLib-based ones; the original mod must be installed on the client.
- The protocol carries position, rotation, velocity, tick age, generic flags, pose, and equipment. **It does not currently reproduce arbitrary mod-specific synchronized entity data, boss attack animation triggers, or server events.** King/Queen baseline fly/idle animations can run, but exact attack-state parity may require an optional Antarchy-specific state adapter.
- SeeU Extra scans *already-loaded server entities* and retains up to 1,024 last-known snapshots per dimension for 10 minutes after unload. **Other unloaded mobs do not tick or move**: their remote visuals stay at the last-known location while the client animation continues. The King is the only opt-in exception with a persistent logical off-chunk flight state; the real Antarchy combat entity is materialized only when normal entity-ticking terrain is present. All other entity types remain snapshot-only. Entities never observed while loaded cannot be reconstructed, and expired snapshots disappear. Detected death/discard removals are dropped; other last-known visuals can become stale until retention expires.
- Client and server both quarantine entity types whose proxy creation or snapshot processing fails; consult `latest.log` for the first failure.

## Build

Run `./gradlew clean build` with Java 21. The Fabric and NeoForge JARs are created under their respective `build/libs` directories. Do not mix them with old upstream 0.1.x builds.

## Supported versions

| Branch | Minecraft | Java | Loaders |
| --- | --- | --- | --- |
| `multiloader-1.21.1` | 1.21.1 | 21 | Fabric, NeoForge |

See [LICENSE](LICENSE).
