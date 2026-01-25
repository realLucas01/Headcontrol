package net.Gamesco.MovementDemo;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

//Klasse um Keybinds zu verwalten
public final class Keybindings {
    public static final Keybindings INSTANCE = new Keybindings();

    //Konstruktur
    private Keybindings() {
    }

    //Man könnte eigene Kategorie für unseren Keybind, hab es aber mal in die Kategorie "Gameplay" gepackt
    //private static final String CATEGORY = "key.categories." + MovementDemo.MOD_ID;

    //Keybind erstellen
    public final KeyMapping toggleActivationState = new KeyMapping(
            "key." + MovementDemo.MOD_ID + ".toggle_activation_state",
            KeyConflictContext.IN_GAME,
            InputConstants.getKey(InputConstants.KEY_B, -1),
            "key.categories.headcontrol"
    );
}
