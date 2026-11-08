package dev.keryeshka.seeu.extra.neoforge.client;

import dev.keryeshka.seeu.extra.client.ShaderPackState;
import dev.keryeshka.seeu.extra.client.DistantEntityVisualHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional, shader-only depth bridge for the distant King. NeoVoxy keeps its
 * Iris LOD depth separate from the entity framebuffer, so merely moving the
 * entity render stage cannot make terrain occlude it.
 *
 * Uses NeoVoxy's existing Sable depth compositor without any Voxy, Iris or
 * Minecraft mixins. It brackets ONLY the King's batch, and scissors the depth
 * passes to the conservative projected bounds of its animated model.
 */
public final class VoxyEntityDepthBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU Extra");

    private static final float[] FULL_SCREEN = {-1.0F, -1.0F, 1.0F, 1.0F};
    private static final Method BEGIN_IN_PLACE;
    private static final Method END_IN_PLACE;
    private static boolean warnedUnavailable;
    private static boolean broken;

    static {
        Method begin = null, end = null;
        try {
            Class<?> shim = Class.forName(
                    "me.cortex.voxy.client.compat.sable.VoxySableDepthShim",
                    false, VoxyEntityDepthBridge.class.getClassLoader());
            begin = shim.getMethod("beginInPlace", Matrix4f.class, Matrix4f.class, float[].class);
            end = shim.getMethod("endInPlace");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Older Voxy builds have no compatible helper. Leave their render path alone.
        }
        BEGIN_IN_PLACE = begin;
        END_IN_PLACE = end;
    }

    private VoxyEntityDepthBridge() {}

    public static void submit(
            String typeId, Entity entity, Vec3 position, Vec3 cameraPosition,
            Matrix4f modelView, Matrix4f projection,
            MultiBufferSource.BufferSource consumers, Runnable draw
    ) {
        if (!ShaderPackState.isActive()
                || !beyondNativeChunks(position, cameraPosition) || broken) {
            draw.run();
            return;
        }
        if (BEGIN_IN_PLACE == null || END_IN_PLACE == null) {
            if (!warnedUnavailable) {
                LOGGER.warn("NeoVoxy depth compositor is unavailable; shader-only King/LOD occlusion needs a NeoVoxy build with VoxySableDepthShim");
                warnedUnavailable = true;
            }
            draw.run();
            return;
        }

        // Force prior proxies into their original framebuffer before changing
        // the depth attachment; flush the King's model while the merged depth
        // is live, not later in the generic AFTER_ENTITIES endBatch.
        consumers.endBatch();
        DistantEntityVisualHooks.Adapter adapter = DistantEntityVisualHooks.adapter(typeId);
        float margin = adapter != null ? (float) adapter.renderMargin(entity)
                : (float) Math.max(4.0D, entity.getBbWidth() * 2.0D);
        float[] bounds = projectedBounds(position, cameraPosition, modelView, projection, margin);
        boolean began = false;
        try {
            BEGIN_IN_PLACE.invoke(null, new Matrix4f(modelView), new Matrix4f(projection), bounds);
            began = true;
        } catch (ReflectiveOperationException | LinkageError failure) {
            broken = true;
            LOGGER.warn("Disabling optional King/NeoVoxy depth compositing after a bridge failure", failure);
        }
        try {
            draw.run();
            consumers.endBatch();
        } finally {
            if (began) {
                try {
                    END_IN_PLACE.invoke(null);
                } catch (IllegalAccessException | InvocationTargetException | LinkageError failure) {
                    broken = true;
                    LOGGER.warn("Failed to restore King/NeoVoxy depth after rendering", failure);
                }
            }
        }
    }

    private static boolean beyondNativeChunks(Vec3 position, Vec3 camera) {
        double edge = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0D;
        return Math.max(Math.abs(position.x - camera.x), Math.abs(position.z - camera.z)) > edge;
    }

    private static float[] projectedBounds(
            Vec3 position, Vec3 camera, Matrix4f modelView, Matrix4f projection, float modelMargin
    ) {
        Matrix4f vp = new Matrix4f(projection).mul(modelView);
        double cx = position.x - camera.x;
        double cy = position.y - camera.y;
        double cz = position.z - camera.z;
        float minX = 1, minY = 1, maxX = -1, maxY = -1;
        // Conservatively include wings, tail and long animations. If any
        // corner crosses the near plane, prefer the whole viewport to a
        // cropped region that could accidentally erase part of the King.
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Vector4f clip = vp.transform(new Vector4f(
                            (float) (cx + x * modelMargin),
                            (float) (cy + y * modelMargin),
                            (float) (cz + z * modelMargin), 1));
                    if (!(clip.w > 0.001F) || !Float.isFinite(clip.w)) {
                        return FULL_SCREEN;
                    }
                    float px = clip.x / clip.w;
                    float py = clip.y / clip.w;
                    if (!Float.isFinite(px) || !Float.isFinite(py)) return FULL_SCREEN;
                    minX = Math.min(minX, px);
                    minY = Math.min(minY, py);
                    maxX = Math.max(maxX, px);
                    maxY = Math.max(maxY, py);
                }
            }
        }
        // TAA jitter/wing overshoot margin; limits four/five fullscreen
        // depth passes to a small patch for a distant, single world boss.
        float slack = 0.03F;
        return new float[] {
                Math.max(-1, minX - slack), Math.max(-1, minY - slack),
                Math.min(1, maxX + slack), Math.min(1, maxY + slack)
        };
    }
}
