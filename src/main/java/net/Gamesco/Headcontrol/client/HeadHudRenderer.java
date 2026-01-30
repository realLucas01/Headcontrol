package net.Gamesco.Headcontrol.client;

import face.tracking.FXController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;

public class HeadHudRenderer {

    public static void render(GuiGraphics g, DeltaTracker delta) {
        // Mod global OFF → nichts anzeigen
        if (!HeadControlState.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();

        // Nur ingame (nicht im Hauptmenü)
        if (mc.player == null || mc.level == null) return;

        // Tracking muss laufen + kalibriert sein
        FXController fx = FXController.instance;
        if (fx == null || !fx.isCameraActive() || !fx.isCalibrated()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Panel-Layout (oben rechts)
        int grid = 90;
        int barW = 12;
        int barH = 90;
        int gap = 12;
        int pad = 14;

        int x0 = sw - pad - (grid + gap + barW);
        int y0 = pad;

        int bg = 0xCC0B0F14;      // dunkles Panel
        int line = 0xFF2A3340;    // Linien
        int text = 0xFFE5E7EB;    // Text
        int neutral = 0xFF22C55E; // grün
        int active = 0xFFEAB308;  // gelb
        int white = 0xFFF3F4F6;

        // Daten holen (deine Getter)
        double yaw = fx.getUiYaw();
        double pitch = fx.getUiPitch();
        double relZ = fx.getUiRelZ();

        // Skalierung
        double YAW_MAX = 25.0;
        double PITCH_MAX = 18.0;
        double Z_MAX = 70.0;

        // Panel Hintergrund
        g.fill(x0 - 10, y0 - 10, x0 + grid + gap + barW + 10, y0 + Math.max(grid, barH) + 34, bg);

        // Titel
        g.drawString(mc.font, "HeadControl", x0, y0 - 2, text, false);

        // ===== Grid =====
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
        int px = cx + (int)Math.round(clamp(yaw / YAW_MAX, -1, 1) * (grid / 2.0 - 12));
        int py = cy + (int)Math.round(clamp(pitch / PITCH_MAX, -1, 1) * (grid / 2.0 - 12));

        int dotColor = (fx.getHeadState() == FXController.HeadState.NEUTRAL) ? neutral : active;
        drawDot(g, px, py, 5, dotColor);

        // ===== Lean-Bar =====
        int bx = gx + grid + gap;
        int by = gy;

        g.fill(bx, by, bx + barW, by + barH, 0xAA000000);

        double thresh = 45.0;
        int mid = by + barH / 2;
        int t1 = mid - (int)Math.round(clamp(thresh / Z_MAX, 0, 1) * (barH / 2.0 - 8));
        int t2 = mid + (int)Math.round(clamp(thresh / Z_MAX, 0, 1) * (barH / 2.0 - 8));
        g.hLine(bx - 3, bx + barW + 3, t1, white);
        g.hLine(bx - 3, bx + barW + 3, t2, white);

        double zNorm = clamp((-relZ) / Z_MAX, -1, 1);
        int my = mid + (int)Math.round(zNorm * (barH / 2.0 - 10));

        int leanColor = (fx.getLeanState() == FXController.LeanState.NEUTRAL) ? neutral : active;
        drawDot(g, bx + barW / 2, my, 5, leanColor);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void drawDot(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x - r, y - r, x + r, y + r, color);
    }
}
