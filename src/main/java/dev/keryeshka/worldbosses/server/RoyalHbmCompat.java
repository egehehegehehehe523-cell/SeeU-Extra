package dev.keryeshka.worldbosses.server;

import dev.keryeshka.worldbosses.WorldBossesOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional HBM bridge. Only World Bosses links to this class; there is no
 * hard HBM dependency and no HBM classes bundled into the addon.
 *
 * HBM 1.21.1 comes from the shared NTM: Continuum source tree. Reflection is
 * intentionally limited to the two public HBM entry points; a missing or
 * changed HBM API falls back to Antarchy's original black hole.
 */
public final class RoyalHbmCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("World Bosses");
    public static final String HOLE_MARKER = "world_bosses_queen_hbm_hole";
    public static final String HOLE_AGE = "world_bosses_queen_hbm_age";
    public static final String HOLE_LIFETIME = "world_bosses_queen_hbm_lifetime";
    public static final String HOLE_MAX_SIZE = "world_bosses_queen_hbm_max_size";
    private static final ResourceLocation HBM_HOLE =
            ResourceLocation.fromNamespaceAndPath("hbm", "entity_black_hole");
    private static volatile WorldBossesOptions options = new WorldBossesOptions();
    private static boolean warnedNuke;
    private static boolean warnedHole;

    private RoyalHbmCompat() {}

    public static void configure(WorldBossesOptions settings) {
        options = settings;
    }

    public static boolean hbmAbilitiesReady() {
        return options.hbmAbilities && ModList.get().isLoaded("hbm");
    }

    public static boolean royalImmunityEnabled() {
        return options.royalHbmImmunity && ModList.get().isLoaded("hbm");
    }

    public static boolean isRoyal(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && "antarchy".equals(id.getNamespace())
                && ("king".equals(id.getPath()) || "queen".equals(id.getPath()));
    }

    /** Only exclude companions and royal projectiles from the Queen's own HBM hole. */
    public static boolean isQueenMinionOrRoyalProjectile(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id != null && "antarchy".equals(id.getNamespace())) {
            // These are Antarchy's royal-specific attack entities, including
            // the non-Projectile ice-spike and black-hole variants.
            switch (id.getPath()) {
                case "manticore", "royal_bolt", "royal_elemental_projectile",
                        "royal_ice_spike", "royal_assailant_black_hole",
                        "royal_black_hole" -> { return true; }
            }
        }
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return (owner != null && isRoyal(owner))
                    || (id != null && "antarchy".equals(id.getNamespace()));
        }
        return false;
    }

    public static boolean excludeFromBlackHole(Entity entity, boolean queenOwnedHole) {
        if (royalImmunityEnabled() && isRoyal(entity)) return true;
        return queenOwnedHole && isQueenMinionOrRoyalProjectile(entity);
    }

    /** Extra fallback for nonstandard NTM blast, radiation and black-hole damage sources. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!royalImmunityEnabled() || !isRoyal(event.getEntity())) return;
        ResourceLocation damageId = event.getSource().typeHolder().unwrapKey()
                .map(key -> key.location()).orElse(null);
        boolean fromHbm = damageId != null && "hbm".equals(damageId.getNamespace());
        boolean relevantHbm = fromHbm && (
                damageId.getPath().contains("nuclear")
                || damageId.getPath().contains("blast")
                || damageId.getPath().contains("balefire")
                || damageId.getPath().contains("blackhole")
                || damageId.getPath().contains("radiation")
                || damageId.getPath().contains("digamma"));
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION) || relevantHbm) {
            event.setCanceled(true);
        }
    }

    /**
     * Use NTM's custom bomb pipeline, not vanilla TNT. Nuclear = King;
     * antimatter/Balefire = Queen. Its native Torex and world updates still
     * fire, so the existing distant HBM/Voxy sync mod can observe them.
     */
    public static boolean detonateRoyalBeam(ServerLevel level, Entity royal, Vec3 hit) {
        if (!hbmAbilitiesReady() || hit == null || !isRoyal(royal)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(royal.getType());
        boolean queen = id != null && "queen".equals(id.getPath());
        try {
            Class<?> custom = Class.forName("com.hbm.explosion.ExplosionNukeCustom");
            Method detonate = custom.getMethod("explodeCustom",
                    Level.class, UUID.class,
                    double.class, double.class, double.class,
                    float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class);
            float strength = queen
                    ? Math.max(1, Math.min(350, options.queenBalefireStrength))
                    : Math.max(1, Math.min(200, options.kingNuclearStrength));
            // No TNT stage: the King gets genuine nuclear, Queen gets genuine Balefire.
            detonate.invoke(null, level, royal.getUUID(),
                    hit.x, hit.y, hit.z,
                    0F, queen ? 0F : strength, 0F, queen ? strength : 0F,
                    0F, 0F, 0F);
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            if (!warnedNuke) {
                warnedNuke = true;
                LOGGER.error("World Bosses could not invoke the NTM custom nuclear explosion API", error);
            }
            return false;
        }
    }

    /** Extract Antarchy's already-selected destination, rather than choosing a new point. */
    public static Vec3 nativeQueenHoleDestination(Entity antarchyProjectile) {
        try {
            Field field = antarchyProjectile.getClass().getDeclaredField("destination");
            field.setAccessible(true);
            return field.get(antarchyProjectile) instanceof Vec3 result ? result : null;
        } catch (ReflectiveOperationException | SecurityException error) {
            if (!warnedHole) {
                warnedHole = true;
                LOGGER.warn("Cannot read Antarchy Queen black-hole destination; retaining native ability", error);
            }
            return null;
        }
    }

    /**
     * Spawn HBM's registered black-hole entity. Its mixin manages growth and
     * expiry using persistent NBT, including after a world reload.
     */
    public static boolean spawnQueenHole(ServerLevel level, Vec3 anchor, UUID queenId) {
        if (!hbmAbilitiesReady() || anchor == null) return false;
        try {
            // NTM discards black holes in peaceful/excluded dimensions.
            // Keep Antarchy's original ability there rather than spawning
            // an HBM entity that immediately vanishes.
            Class<?> compatibility = Class.forName("com.hbm.config.CompatibilityConfig");
            Object warDim = compatibility.getMethod("isWarDim", Level.class).invoke(null, level);
            if (Boolean.FALSE.equals(warDim)) return false;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(HBM_HOLE).orElse(null);
            if (type == null) return false;
            Entity hole = type.create(level);
            if (hole == null) return false;
            hole.getClass().getMethod("setSize", float.class).invoke(hole, 0.45F);
            // The entity's existing ownership handling is retained when available.
            try {
                hole.getClass().getMethod("setProtectionOwner", UUID.class).invoke(hole, queenId);
            } catch (ReflectiveOperationException ignored) {
                // Not every NTM variant exposes protection ownership.
            }
            hole.getPersistentData().putBoolean(HOLE_MARKER, true);
            hole.getPersistentData().putInt(HOLE_AGE, 0);
            hole.getPersistentData().putInt(HOLE_LIFETIME,
                    Math.max(20, Math.min(400, options.queenBlackHoleLifetimeTicks)));
            hole.getPersistentData().putFloat(HOLE_MAX_SIZE,
                    Math.max(0.5F, Math.min(16F, options.queenBlackHoleMaxSize)));
            hole.setPos(anchor.x, anchor.y, anchor.z);
            return level.addFreshEntity(hole);
        } catch (ReflectiveOperationException | LinkageError error) {
            if (!warnedHole) {
                warnedHole = true;
                LOGGER.error("Could not spawn HBM Queen black hole; using Antarchy original", error);
            }
            return false;
        }
    }
}
