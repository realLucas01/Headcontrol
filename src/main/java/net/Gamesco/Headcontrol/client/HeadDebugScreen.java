package net.Gamesco.Headcontrol.client;

import face.tracking.FXController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HeadDebugScreen extends Screen {

    public HeadDebugScreen() {
        super(Component.literal("HeadControl UI"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    // 1) Erst Minecraft rendern lassen (inkl. Blur im Hintergrund)
    super.render(g, mouseX, mouseY, partialTick);

    Minecraft mc = Minecraft.getInstance();

    int sw = this.width;

    // Panel-Layout (oben rechts)
    int grid = 90;
    int barW = 12;
    int barH = 90;
    int gap = 12;
    int pad = 14;

    int x0 = sw - pad - (grid + gap + barW);
    int y0 = pad;

    int bg = 0xCC0B0F14;      // dunkles Panel (leicht transparent)
    int line = 0xFF2A3340;    // Linien dunkelgrau
    int text = 0xFFE5E7EB;    // hellgrau/weiß
    int neutral = 0xFF22C55E; // grün
    int active = 0xFFEAB308;  // gelb
    int warn = 0xFFEF4444;    // rot
    int white = 0xFFF3F4F6;

    // Daten holen
    FXController fx = FXController.instance;

    boolean ok = (fx != null && fx.isCameraActive() && fx.isCalibrated());
    int stateColor = ok ? neutral : warn;

    double yaw = (fx != null) ? fx.getUiYaw() : 0.0;
    double pitch = (fx != null) ? fx.getUiPitch() : 0.0;
    double relZ = (fx != null) ? fx.getUiRelZ() : 0.0;

    // Skalierung (kannst du später feinjustieren)
    double YAW_MAX = 25.0;
    double PITCH_MAX = 18.0;
    double Z_MAX = 70.0;

    // Panel Hintergrund
    g.fill(x0 - 10, y0 - 10, x0 + grid + gap + barW + 10, y0 + Math.max(grid, barH) + 34, bg);

    // Titel
    g.drawString(mc.font, "HeadControl", x0, y0 - 2, text, false);

    // ===== Steuerkreuz (Grid) =====
    int gx = x0;
    int gy = y0 + 14;

    g.fill(gx, gy, gx + grid, gy + grid, 0xAA000000);

    int cx = gx + grid / 2;
    int cy = gy + grid / 2;

    // Rahmen + Mittellinien
    g.hLine(gx, gx + grid, gy, line);
    g.hLine(gx, gx + grid, gy + grid, line);
    g.vLine(gx, gy, gy + grid, line);
    g.vLine(gx + grid, gy, gy + grid, line);

    g.hLine(gx + 8, gx + grid - 8, cy, line);
    g.vLine(cx, gy + 8, gy + grid - 8, line);

    // Punkt
    int px = cx + (int) Math.round(clamp(yaw / YAW_MAX, -1, 1) * (grid / 2.0 - 12));
    int py = cy + (int) Math.round(clamp(pitch / PITCH_MAX, -1, 1) * (grid / 2.0 - 12));
    drawDot(g, px, py, 5,
            (fx != null && fx.getHeadState() == FXController.HeadState.NEUTRAL) ? neutral : active);

    // ===== Lean-Bar =====
    int bx = gx + grid + gap;
    int by = gy;

    g.fill(bx, by, bx + barW, by + barH, 0xAA000000);

    double thresh = 45.0;
    int mid = by + barH / 2;
    int t1 = mid - (int) Math.round(clamp(thresh / Z_MAX, 0, 1) * (barH / 2.0 - 8));
    int t2 = mid + (int) Math.round(clamp(thresh / Z_MAX, 0, 1) * (barH / 2.0 - 8));
    g.hLine(bx - 3, bx + barW + 3, t1, white);
    g.hLine(bx - 3, bx + barW + 3, t2, white);

    // relZ negativ = forward -> Marker nach oben
    double zNorm = clamp((-relZ) / Z_MAX, -1, 1);
    int my = mid + (int) Math.round(zNorm * (barH / 2.0 - 10));
    int leanColor = (fx != null && fx.getLeanState() == FXController.LeanState.NEUTRAL) ? neutral : active;
    drawDot(g, bx + barW / 2, my, 5, leanColor);

    // Statuszeile
    String status = (fx == null) ? "FX: not started"
            : (!fx.isCameraActive() ? "Tracking: OFF"
            : (!fx.isCalibrated() ? "Tracking: CAL..."
            : "Tracking: ON"));

    g.drawString(mc.font, status, x0, gy + grid + 8, stateColor, false);

    // Hinweis
    g.drawString(mc.font, "N to open • ESC to close", x0, gy + grid + 20, 0xFF9CA3AF, false);
}


    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void drawDot(GuiGraphics g, int cx, int cy, int r, int argb) {
        g.fill(cx - r, cy - r, cx + r, cy + r, argb);
    }
}
