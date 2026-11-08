# SeeU Extra (Minecraft 1.21.1)

This fork renders **opted-in non-player entities** beyond Minecraft's normal render/tracking range using [SeeU](https://github.com/cat4blep/SeeU). It supports Fabric and NeoForge, with no Neo-Voxy or Minecraft mixins.

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
- `entityCap` and `updateIntervalTicks` bound server traffic and snapshot work. The server scans **only types requested by active clients**.
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

- Outside vanilla rendering range, SeeU Extra uses a continuously interpolated snapshot-backed entity proxy—even when a stale vanilla client entity is still present. This avoids frozen positions and tick-based locomotion animations at the handoff.
- Animated model visibility is tested using expanded bounds so wings and large geometry are not culled at the collision-box edge.
- Proxies use normal registered mod entity renderers, including GeckoLib-based ones; the original mod must be installed on the client.
- The protocol carries position, rotation, velocity, tick age, generic flags, pose, and equipment. **It does not currently reproduce arbitrary mod-specific synchronized entity data, boss attack animation triggers, or server events.** King/Queen baseline fly/idle animations can run, but exact attack-state parity may require an optional Antarchy-specific state adapter.
- SeeU Extra only scans *already-loaded server entities*; it never force-loads chunks. Distant entities in unloaded chunks cannot be shown.
- Client and server both quarantine entity types whose proxy creation or snapshot processing fails; consult `latest.log` for the first failure.

## Build

Run `./gradlew clean build` with Java 21. The Fabric and NeoForge JARs are created under their respective `build/libs` directories. Do not mix them with old upstream 0.1.x builds.

## Supported versions

| Branch | Minecraft | Java | Loaders |
| --- | --- | --- | --- |
| `multiloader-1.21.1` | 1.21.1 | 21 | Fabric, NeoForge |

See [LICENSE](LICENSE).
