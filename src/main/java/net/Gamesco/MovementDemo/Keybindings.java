package net.Gamesco.MovementDemo;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

public final class Keybindings {
    public static final Keybindings INSTANCE = new Keybindings();

    private Keybindings() {
    }

    //private static final String CATEGORY = "key.categories." + MovementDemo.MOD_ID;

    public final KeyMapping toggleActivationState = new KeyMapping(
            "key." + MovementDemo.MOD_ID + ".toggle_activation_state",
            KeyConflictContext.IN_GAME,
            InputConstants.getKey(InputConstants.KEY_B, -1),
            KeyMapping.CATEGORY_GAMEPLAY
    );

}
