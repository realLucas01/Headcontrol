package net.Gamesco.MovementDemo.client;

import face.tracking.FXController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HeadControlScreen extends Screen {

    private int cameraIndex = 0; // default
    private Button btnEnabled, btnCamera, btnStartStop, btnCalibrate;

    public HeadControlScreen() {
        super(Component.literal("HeadControl"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int w = 220;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 60;

        btnEnabled = Button.builder(labelEnabled(), b -> {
            HeadControlState.toggle();

            // Optional: beim OFF direkt Kamera stoppen
            FXController fx = FXController.instance;
            if (!HeadControlState.isEnabled() && fx != null) fx.stopCameraNow();

            b.setMessage(labelEnabled());
            refreshDynamicLabels();
        }).bounds(x, y, w, 20).build();

        btnCamera = Button.builder(labelCamera(), b -> {
            cameraIndex = (cameraIndex + 1) % 3; // 0..2 (anpassen wenn du mehr willst)
            b.setMessage(labelCamera());
        }).bounds(x, y + 24, w, 20).build();

        btnStartStop = Button.builder(Component.literal("Camera: ..."), b -> {
            if (!HeadControlState.isEnabled()) return;

            FXController fx = FXController.instance;
            if (fx != null) {
                fx.toggleCamera(cameraIndex);
            }
            refreshDynamicLabels();
        }).bounds(x, y + 48, w, 20).build();

        btnCalibrate = Button.builder(Component.literal("Recalibrate"), b -> {
            if (!HeadControlState.isEnabled()) return;
            FXController fx = FXController.instance;
            if (fx != null) fx.recalibrate();
            refreshDynamicLabels();
        }).bounds(x, y + 72, w, 20).build();

        addRenderableWidget(btnEnabled);
        addRenderableWidget(btnCamera);
        addRenderableWidget(btnStartStop);
        addRenderableWidget(btnCalibrate);

        refreshDynamicLabels();
    }

    private void refreshDynamicLabels() {
        FXController fx = FXController.instance;

        // Buttons deaktivieren wenn Mod OFF
        boolean en = HeadControlState.isEnabled();
        btnCamera.active = en;
        btnStartStop.active = en;
        btnCalibrate.active = en;

        // Start/Stop Label
        if (fx == null) {
            btnStartStop.setMessage(Component.literal("Camera: (FX not started)").withStyle(ChatFormatting.GRAY));
        } else {
            btnStartStop.setMessage(
                    fx.isCameraActive()
                            ? Component.literal("Camera: STOP").withStyle(ChatFormatting.RED)
                            : Component.literal("Camera: START").withStyle(ChatFormatting.GREEN)
            );
        }

        btnEnabled.setMessage(labelEnabled());
        btnCamera.setMessage(labelCamera());
    }

    private Component labelEnabled() {
        return HeadControlState.isEnabled()
                ? Component.literal("HeadControl: ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("HeadControl: OFF").withStyle(ChatFormatting.RED);
    }

    private Component labelCamera() {
        return Component.literal("Camera: " + cameraIndex).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // kleines Info-Panel
        int x = this.width / 2 - 110;
        int y = this.height / 2 + 18;

        g.drawString(this.font, "N to open • ESC to close", x, y + 78, 0xFF9CA3AF, false);

        FXController fx = FXController.instance;
        if (fx != null) {
            String s1 = "Active: " + fx.isCameraActive();
            String s2 = "Calibrated: " + fx.isCalibrated();
            g.drawString(this.font, s1, x, y + 90, 0xFFE5E7EB, false);
            g.drawString(this.font, s2, x, y + 102, 0xFFE5E7EB, false);
        }
    }
}
