package dev.keryeshka.seeu.extra.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keryeshka.seeu.extra.SeeUExtra;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = SeeUExtra.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SeeUExtraNeoForgeKeys {
    public static final KeyMapping CONFIGURE = new KeyMapping(
            "key.seeu_extra.configure",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.seeu_extra"
    );

    private SeeUExtraNeoForgeKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CONFIGURE);
    }
}
