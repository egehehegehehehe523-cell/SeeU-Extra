# SeeU Extra Continuation — Minecraft 1.21.1

General-purpose distant non-player entity rendering through the [SeeU](https://github.com/cat4blep/SeeU) addon API. **This branch contains no Antarchy King or Queen simulation**: those mechanics belong to the separate World Bosses mod.

## F8 distant-entity configuration

Press **F8** to search registered entity types and opt them in one at a time. Every type is OFF initially. Set a distance in blocks for each enabled type, and optionally enable **Anim ON** for a persistent synthetic animation controller during native-chunk handoffs. The server's allow/deny selector, per-type client limits, entity caps and snapshot interval still apply. Configuration is saved under `config/seeu-extra-client.json`.

`Anim ON` is a generic GeckoLib-friendly controller-retention option. It cannot automatically suppress another mod's native renderer inside normal chunks; an external adapter should register the entity through `DistantEntityVisualHooks` and suppress native drawing through the owning mod's renderer hook. The World Bosses addon provides this for Antarchy.

## Iris + NeoVoxy

On NeoForge, SeeU Extra automatically applies the shader-only light/glow correction and NeoVoxy LOD-depth merge to **any enabled distant entity**, not just King/Queen. It reuses NeoVoxy's existing optional `VoxySableDepthShim` integration without mixing into NeoVoxy, Iris, or Minecraft. The extra depth work only runs beyond vanilla render chunks with an active shader pack, is scissored to conservative model bounds, and skips automatically when the compatible NeoVoxy helper is unavailable.

Unloaded ordinary entities are represented by last-known snapshots and **do not simulate AI or combat**. Persistent worldboss motion belongs to the separate World Bosses mod, which registers with SeeU Extra's `DistantEntityServerHooks`. The normal renderer is independent of that addon and works without Antarchy installed.

## Extending SeeU Extra

- `DistantEntityVisualHooks.register(typeId, adapter)` — optional client animation/renderer state synchronization, native-render takeover and model extent
- `DistantEntityServerHooks.register(extension)` — optional server-driven offloaded snapshots, priority entities and dimension-wide boss bars
- `ExtraEntityBroadcaster.captureSnapshot(entity)` — capture authoritative data before an add-on serializes and offloads an entity
- `SeeUExtraNeoForgeClient.ownsNativeRender(entity)` and `animationCacheForNative(entity)` — public hooks for add-on-owned entity renderer/GeckoLib mixins

The network protocol is **version 4**. Both server and client must use the same Continuation build. Existing JSON client settings migrate automatically, with persistent animation disabled for older entries unless explicitly opted in.

## Build

Java 21, Minecraft 1.21.1. Run `./gradlew clean build` to produce separate Fabric and NeoForge JARs in each loader's `build/libs`. Do not install this fork alongside the old SeeU Extra; they share the `seeu_extra` mod ID.

This branch retains the F8 interface, per-type render distances, configurable server restrictions, interpolation, proxy quarantine and long-range support independently of World Bosses.

### Third-party opt-in defaults

The NeoForge client exposes
`SeeUExtraNeoForgeClient.enableDistantEntityByDefaultOnce(id, distanceBlocks)`
for independent addons such as World Bosses. It adds an F8 distant-entity
preset only on that addon's first installation. It never bundles addon logic,
and the user can then disable it or alter the distance in F8 without being
overridden on later launches. Other entities remain disabled by default.
