package app;

import java.util.prefs.Preferences;

public class AppSettings {
    private static final Preferences prefs = Preferences.userNodeForPackage(AppSettings.class);

    public static void saveDividerPosition(double pos) {
        prefs.putDouble("dividerPos", pos);
    }

    public static double loadDividerPosition(double defaultPos) {
        return prefs.getDouble("dividerPos", defaultPos);
    }

    public static void saveWindowBounds(double x, double y, double w, double h) {
        prefs.putDouble("winX", x);
        prefs.putDouble("winY", y);
        prefs.putDouble("winW", w);
        prefs.putDouble("winH", h);
    }

    public static double[] loadWindowBounds(double fallbackW, double fallbackH) {
        return new double[] {
            prefs.getDouble("winX", Double.NaN),
            prefs.getDouble("winY", Double.NaN),
            prefs.getDouble("winW", fallbackW),
            prefs.getDouble("winH", fallbackH)
        };
    }

    public static void saveLastOpenedFile(String path) {
        prefs.put("lastFile", path);
    }

    public static String loadLastOpenedFile() {
        return prefs.get("lastFile", null);
    }
}
