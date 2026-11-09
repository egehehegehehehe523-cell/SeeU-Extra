package dev.keryeshka.worldbosses.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/**
 * Antarchy's RoyalAttackScheduler.cooldowns and the per-royal integer
 * cooldown fields are not written to the normal entity NBT. Save them
 * separately and keep them ticking in the logical offloaded state.
 *
 * Only cooldown counters are transferred. Active scripted Action callbacks
 * (which contain live entity references) are deliberately never serialized.
 */
public final class RoyalAttackPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger("World Bosses");
    private static boolean warned;
    private RoyalAttackPersistence() {}

    public static CompoundTag capture(Entity royal) {
        CompoundTag state = new CompoundTag();
        CompoundTag fields = new CompoundTag();
        for (Class<?> type = royal.getClass(); type != null && isRoyalClass(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())
                        || !field.getName().toLowerCase(Locale.ROOT).contains("cooldown")) continue;
                try {
                    field.setAccessible(true);
                    fields.putInt(type.getSimpleName() + "." + field.getName(),
                            Math.max(0, field.getInt(royal)));
                } catch (ReflectiveOperationException | RuntimeException e) { warn(e); }
            }
        }
        state.put("Fields", fields);
        CompoundTag scheduler = new CompoundTag();
        try {
            Object attackScheduler = fieldNamed(royal.getClass(), "attackScheduler").get(royal);
            Object value = fieldNamed(attackScheduler.getClass(), "cooldowns").get(attackScheduler);
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key && entry.getValue() instanceof Integer ticks)
                        scheduler.putInt(key, Math.max(0, ticks));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) { warn(e); }
        state.put("Scheduler", scheduler);
        return state;
    }

    public static void tick(CompoundTag state) {
        if (state.isEmpty()) return;
        advance(state.getCompound("Fields"));
        advance(state.getCompound("Scheduler"));
    }

    private static void advance(CompoundTag section) {
        for (String key : section.getAllKeys()) {
            section.putInt(key, Math.max(0, section.getInt(key) - 1));
        }
    }

    public static void restore(Entity royal, CompoundTag state) {
        if (state.isEmpty()) return; // Migration: old worlds have no snapshot.
        CompoundTag fields = state.getCompound("Fields");
        for (Class<?> type = royal.getClass(); type != null && isRoyalClass(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String key = type.getSimpleName() + "." + field.getName();
                if (!fields.contains(key) || field.getType() != int.class
                        || Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    field.setInt(royal, fields.getInt(key));
                } catch (ReflectiveOperationException | RuntimeException e) { warn(e); }
            }
        }
        CompoundTag scheduler = state.getCompound("Scheduler");
        try {
            Object attackScheduler = fieldNamed(royal.getClass(), "attackScheduler").get(royal);
            Object value = fieldNamed(attackScheduler.getClass(), "cooldowns").get(attackScheduler);
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Integer> target = (Map<String, Integer>) map;
                target.clear();
                for (String id : scheduler.getAllKeys())
                    target.put(id, Math.max(0, scheduler.getInt(id)));
            }
        } catch (ReflectiveOperationException | RuntimeException e) { warn(e); }
    }

    private static boolean isRoyalClass(Class<?> type) {
        return type.getName().startsWith("com.craisinlord.antarchy.content.entity.royal.");
    }

    private static Field fieldNamed(Class<?> from, String name) throws NoSuchFieldException {
        for (Class<?> type = from; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void warn(Exception e) {
        if (!warned) {
            warned = true;
            LOGGER.warn("Could not capture/restore some Antarchy royal attack cooldowns", e);
        }
    }
}
