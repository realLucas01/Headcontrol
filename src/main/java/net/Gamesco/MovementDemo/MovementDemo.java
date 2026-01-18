//Main Class for programming the demo mod for our MMMI project
package net.Gamesco.MovementDemo;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.item.Item;
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
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import face.tracking.StartFaceTracking;
import face.tracking.FXController;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MovementDemo.MOD_ID)
public class MovementDemo {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "movementdemo";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public FXController.HeadState currentHeadState;
    public FXController controller;

    public boolean testBool =true;

    public MovementDemo(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        //context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

    }


    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event)
    {

        //Sets first Item in Inventory(after position) as Item that triggers the automatic walking(Still trying to understand how to specify Items....)
        Item test = event.player.getInventory().items.getFirst().getItem();

        //Player only walks when previously set Item is in Hand and the Player is on the ground(or else we reeeeally accelerate)
        if((event.player.isHolding(test))&&(event.player.onGround())){
            if(testBool)  {
                testBool = false;
                StartFaceTracking.main();
            }
            if(controller!= null){
                currentHeadState = controller.getHeadState();
                System.out.println(">>> BESTÄTIGTER STATUS: " + currentHeadState);
            }

            //Make the player walk forward in standard walking speed.
            event.player.moveRelative(0.1f,new Vec3(0,0,0.5f));
            //KeyMapping.click();
            //new KeyboardInput(KeyMapping.click(key_up));
        }
        //adjust to high speed when Jumping
        else if((event.player.isHolding(test))&&!(event.player.onGround())){
            event.player.moveRelative(0.1f,new Vec3(0,0,0.15f));


        }

        //Fix sprint Button not working when walking forward
        if((event.player.isHolding(test))&&(event.player.isSprinting())){
            //Make the player walk forward in standard walking speed.
            event.player.moveRelative(0.1f,new Vec3(0,0,1));
        }

        //Player only looks up when crouching
        if (event.player.isCrouching()){
            //Make the Player look up, although still quite "stuttery"
            event.player.setXRot(event.player.getXRot()-0.2f);
        }

        if (Keybindings.INSTANCE.toggleActivationState.consumeClick()){
            event.player.giveExperienceLevels(4);
        }

    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.InteractionKeyMappingTriggered event) {
        System.out.println(event.getKeyMapping().getName());
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");

    }

    // EventBusSubscriber is used to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event){
            event.register(Keybindings.INSTANCE.toggleActivationState);
        }
    }
}
