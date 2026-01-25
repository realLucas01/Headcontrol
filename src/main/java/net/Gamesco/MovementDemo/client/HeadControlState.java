package net.Gamesco.MovementDemo.client;

public final class HeadControlState {
    private static volatile boolean enabled = true;
    private HeadControlState() {}
    public static boolean isEnabled() { return enabled; }
    public static void toggle() { enabled = !enabled; }
    public static void setEnabled(boolean v) { enabled = v; }
}
