//Main Class for programming the demo mod for our MMMI project
package net.Gamesco.Headcontrol;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.Gamesco.Headcontrol.client.HeadControlState;
import org.slf4j.Logger;
import face.tracking.StartFaceTracking;
import face.tracking.FXController;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(Headcontrol.MOD_ID)
public class Headcontrol {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "headcontrol";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public FXController controller;

    public Headcontrol(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        //context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

    }


    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (!HeadControlState.isEnabled()) return;
        //set controller
        if (controller == null) {
            controller = FXController.instance;
        }
        //Works if OpenCV is started properly
        if (controller != null) {
            //controlls where player looks
           //System.out.println("JA");
            switch (controller.getHeadState()) {
                case UP:
                    for(int i=0; i <=16 ;i++){event.player.setXRot(event.player.getXRot() - 0.05f);}
                    break;
                case DOWN:
                    for(int i=0; i <=16 ;i++){event.player.setXRot(event.player.getXRot() + 0.05f);}
                    break;
                case LEFT:
                    for(int i=0; i <=16 ;i++){event.player.setYRot(event.player.getYRot() - 0.05f);}
                    break;
                case RIGHT:
                    for(int i=0; i <=16 ;i++){event.player.setYRot(event.player.getYRot() + 0.05f);}
                    break;
            }
            //controls if player walks forwards or backwards
            switch (controller.getLeanState()){
                case FORWARD:
                    if(event.player.onGround()){
                        //Make the player walk backwards in standard walking speed.
                        event.player.moveRelative(0.1f,new Vec3(0,0,0.5f));
                    }
                    //adjust to high speed when Jumping
                    else if(!event.player.onGround()){
                        event.player.moveRelative(0.1f,new Vec3(0,0,0.15f));
                    }
                    break;
                case BACKWARD:
                    if(event.player.onGround()){
                        //Make the player walk backwards in standard walking speed.
                        event.player.moveRelative(0.1f,new Vec3(0,0,-0.5f));
                        //KeyMapping.click();
                        //new KeyboardInput(KeyMapping.click(key_up));
                    }
                    //adjust to high speed when Jumping
                    else if(!event.player.onGround()){
                        event.player.moveRelative(0.1f,new Vec3(0,0,-0.15f));
                    }
                    break;
                case NEUTRAL:
                    break;
            }
            //controlls if Player walks to the left or to the right
            switch (controller.getTiltState()){
                case LEFT:
                    if(event.player.onGround()){
                        //Make the player walk to the left in standard walking speed.
                        event.player.moveRelative(0.1f,new Vec3(0.5f,0,0));
                        //KeyMapping.click();
                        //new KeyboardInput(KeyMapping.click(key_up));
                    }
                    //adjust to high speed when Jumping
                    else if(!event.player.onGround()){
                        event.player.moveRelative(0.1f,new Vec3(0.15f,0,0));
                    }
                    break;
                case RIGHT:
                    if(event.player.onGround()){
                        //Make the player walk to the right in standard walking speed.
                        event.player.moveRelative(0.1f,new Vec3(-0.5f,0,0));
                        //KeyMapping.click();
                        //new KeyboardInput(KeyMapping.click(key_up));
                    }
                    //adjust to high speed when Jumping
                    else if(!event.player.onGround()){
                        event.player.moveRelative(0.1f,new Vec3(-0.15f,0,0));
                    }
                    break;
                case NEUTRAL:
                    break;
            }
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void onKeyMappingPress(InputEvent.Key event) {
        if (!HeadControlState.isEnabled()) return;
        //Nur ausführen wenn unser Keybind gedrückt
        if(Keybindings.INSTANCE.toggleActivationState.isDown()){
            //Öffnen eines neuen Thread für OpenCV, da sonst der Client hängen bleibt oder die tps stirbt
            Thread trackingThread = new Thread(() -> {
                try {
                    // Hier wird die JavaFX Application gestartet
                    StartFaceTracking.main();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            trackingThread.setName("FaceTracking-Thread");
            trackingThread.start();

            /* Versuch den Thread korrekt zu schließen
            if (!trackingThread.isAlive()){

            } else if (trackingThread.isAlive()) {
                trackingThread.interrupt();
            }*/
        }
    }

    // EventBusSubscriber is used to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        //Unseren Keybind beim EventBus registrieren
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event){
            event.register(Keybindings.INSTANCE.toggleActivationState);
        }
    }
}
