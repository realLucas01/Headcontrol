package net.Gamesco.Headcontrol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DebugOverlayMessage {
    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {

        // Mod global OFF → nichts anzeigen

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Wenn das HeadControl-UI offen ist → Hinweis ausblenden
        if (mc.screen instanceof HeadControlScreen) return;


        if (event.phase != TickEvent.Phase.END) return;


        if ((ticks++ % 40) == 0) {
            mc.gui.setOverlayMessage(Component.literal("Press N for HeadControl Settings"), false);
        }
    }
}
