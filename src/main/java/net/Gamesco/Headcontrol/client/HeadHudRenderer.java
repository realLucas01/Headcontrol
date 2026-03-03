package net.Gamesco.Headcontrol.client;

import face.tracking.FXController;
import face.tracking.HeadState;
import face.tracking.HeadTrackingLogic;
import face.tracking.LeanState;
import face.tracking.TrackingDataSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;

public class HeadHudRenderer {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        TrackingDataSnapshot data = HeadTrackingLogic.getInstance().getSnapshot();

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
        double yaw = data.yaw;
        double pitch = data.pitch;
        double relZ = data.z;
        double yawThres = data.yawThres;
        double pitchThres = data.pitchThres;

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
        int cx = gx + grid / 2;
        int cy = gy + grid / 2;

        // Skalierung berechnen (wie groß ist der Schwellenwert im Verhältnis zum Maximum?)
        // Wir nutzen dieselbe Logik wie beim Punkt-Zeichnen
        int thresX = (int) Math.round((data.yawThres / YAW_MAX) * (grid / 2.0 - 12));
        int thresY = (int) Math.round((data.pitchThres / PITCH_MAX) * (grid / 2.0 - 12));
        double scaleX = (grid / 2.0) / YAW_MAX;
        double scaleY = (grid / 2.0) / PITCH_MAX;


        g.fill(gx, gy, gx + grid, gy + grid, 0xAA000000);

        int dx = (int) (yawThres * scaleX);
        int dy = (int) (pitchThres * scaleY);

        // Rahmen + Mittellinien
        int innerLineColor = 0x88555555; // Halbtransparentes Grau
        g.vLine(cx - dx, gy, gy + grid, innerLineColor); // Linke Grenze
        g.vLine(cx + dx, gy, gy + grid, innerLineColor); // Rechte Grenze
        g.hLine(gx, gx + grid, cy - dy, innerLineColor); // Obere Grenze
        g.hLine(gx, gx + grid, cy + dy, innerLineColor); // Untere Grenze

        // Äußerer Rahmen & Mittellinien (statisch zur Orientierung)
        g.hLine(gx, gx + grid, gy, line);
        g.hLine(gx, gx + grid, gy + grid, line);
        g.vLine(gx, gy, gy + grid, line);
        g.vLine(gx + grid, gy, gy + grid, line);


        // Punkt
        int px = cx + (int) Math.round(clamp(yaw * scaleX, -grid/2.0 + 5, grid/2.0 - 5));
        int py = cy + (int) Math.round(clamp(pitch * scaleY, -grid/2.0 + 5, grid/2.0 - 5));

        int dotColor = (data.headState == HeadState.NEUTRAL) ? neutral : active;
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

        int leanColor = (data.leanState == LeanState.NEUTRAL) ? neutral : active;
        drawDot(g, bx + barW / 2, my, 5, leanColor);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void drawDot(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x - r, y - r, x + r, y + r, color);
    }
}