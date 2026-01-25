package net.Gamesco.MovementDemo.client;

import net.Gamesco.MovementDemo.MovementDemo;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientKeybinds {

    public static final KeyMapping OPEN_UI = new KeyMapping(
            "key.movementdemo.open_head_ui",
            GLFW.GLFW_KEY_N,
            "key.categories.headcontrol"
    );


    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_UI);
    }
}
