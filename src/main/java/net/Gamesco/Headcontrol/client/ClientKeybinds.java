package net.Gamesco.Headcontrol.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientKeybinds {

    public static final KeyMapping OPEN_UI = new KeyMapping(
            "key.headcontrol.open_head_ui",
            GLFW.GLFW_KEY_N,
            "key.categories.headcontrol"
    );


    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_UI);
    }
}
