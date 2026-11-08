package dev.keryeshka.seeu.extra.client;

import java.lang.reflect.Method;

/** Optional Iris detection; querying it never makes Iris a required dependency. */
public final class ShaderPackState {
    private static final Method GET_INSTANCE;
    private static final Method SHADERS_IN_USE;

    static {
        Method instance = null;
        Method enabled = null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            instance = api.getMethod("getInstance");
            enabled = api.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Fabric/NeoForge installations without Iris stay on the original path.
        }
        GET_INSTANCE = instance;
        SHADERS_IN_USE = enabled;
    }

    private ShaderPackState() {}

    public static boolean isActive() {
        if (GET_INSTANCE == null || SHADERS_IN_USE == null) return false;
        try {
            return Boolean.TRUE.equals(SHADERS_IN_USE.invoke(GET_INSTANCE.invoke(null)));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }
}
