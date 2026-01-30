package net.Gamesco.Headcontrol.client;

import net.Gamesco.Headcontrol.Headcontrol; // <- ggf. anpassen an euren Main-Mod-Class-Namen/MODID
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Headcontrol.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class HeadHudLayer {

    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Headcontrol.MOD_ID, "headcontrol_hud");


    @SubscribeEvent
    public static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        // Layer hinzufügen (in VANILLA_ROOT)
        event.getLayeredDraw().add(ForgeLayeredDraw.VANILLA_ROOT, LAYER_ID, HeadHudRenderer::render);

        // Position im Stack: z.B. über Debug-Overlay (F3) oder über Chat.
        // Wenn du willst dass es "immer oben drüber" ist: SUBTITLE_OVERLAY ist sehr weit oben.
        event.getLayeredDraw().putAbove(ForgeLayeredDraw.VANILLA_ROOT, LAYER_ID, ForgeLayeredDraw.DEBUG_OVERLAY);
        // Alternative:
        // event.getLayeredDraw().putAbove(ForgeLayeredDraw.VANILLA_ROOT, LAYER_ID, ForgeLayeredDraw.CHAT_OVERLAY);
        // event.getLayeredDraw().putAbove(ForgeLayeredDraw.VANILLA_ROOT, LAYER_ID, ForgeLayeredDraw.SUBTITLE_OVERLAY);
    }
}
