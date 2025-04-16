package app;

import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AppSettings {
    private static final Preferences prefs = Preferences.userNodeForPackage(AppSettings.class);
    private static ObjectMapper mapper = new ObjectMapper();

    static ObjectMapper getMapper() {
        return mapper;
    }

    public static void saveDividerPosition(String name, double pos) {
        prefs.putDouble("dividerPos." + name, pos);
    }

    public static double loadDividerPosition(String name, double defaultPos) {
        return prefs.getDouble("dividerPos." + name, defaultPos);
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

    public static void saveLastOpenedFile(List<String> path) {
        String value = String.join(";", path);
        prefs.put("lastFile", value);
    }

    public static List<String> loadLastOpenedFile() {
        String value = prefs.get("lastFile", null);
        if (value == null) {
            return List.of();
        }
        return List.of(value.split(";"));
    }

    private static final String FILTER_KEY = "filterRules";

    public static void saveFilterRules(Object json) {
        try {
            prefs.put(FILTER_KEY, mapper.writeValueAsString(json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to save filter rules", e);
        }
    }

    public static List<Map<String, String>> loadFilterRules() {
        String json = prefs.get(FILTER_KEY, "");

        List<Map<String, String>> list;
        try {
            list = mapper.readValue(
                    json,
                    new TypeReference<>() {}
                );
            return list;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load filter rules", e);
        }
    }

    public static void saveFileDividerPosition(double pos) {
        prefs.putDouble("fileDivider", pos);
    }

    public static double loadFileDividerPosition(double fallback) {
        return prefs.getDouble("fileDivider", fallback);
    }

    public static void saveFilterDividerPosition(double pos) {
        prefs.putDouble("filterDivider", pos);
    }

    public static double loadFilterDividerPosition(double fallback) {
        return prefs.getDouble("filterDivider", fallback);
    }

    public static void saveTableColumnWidths(String json) {
        prefs.put("tableColumnWidths", json);
    }

    public static String loadTableColumnWidths() {
        return prefs.get("tableColumnWidths", "");
    }

    public static void saveColumnLayout(String key, String json) {
        prefs.put(key, json);
    }

    public static String loadColumnLayout(String key) {
        return prefs.get(key, "");
    }

    public static void saveString(String string, String json) {
        prefs.put(string, json);
    }

    public static String loadString(String string) {
        return prefs.get(string, "");
    }

    public static void saveJson(String key, Object node)
    {
        try {
            String json = mapper.writeValueAsString(node);
            prefs.put(key, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JsonNode loadJson(String key)
    {
        try {
            String json = prefs.get(key, "");
            if (json.isEmpty()) {
                return null;
            }
            return mapper.readTree(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
