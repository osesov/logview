package app.debug;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ArgBuilder {
    private final Map<String, String> map;

    private ArgBuilder() {
        this.map = new HashMap<>();
    }

    public static <V> ArgBuilder of() {
        return new ArgBuilder();
    }

    // public MapBuilder putObject(String key, Object value) {
    //     map.put(key, value);
    //     return this;
    // }

    public ArgBuilder putString(String key, String value) {
        map.put(key, value);
        return this;
    }

    public ArgBuilder putLong(String key, long value) {
        map.put(key, String.valueOf(value));
        return this;
    }

    public Map<String, String> build() {
        return map;
    }

    public Map<String, String> buildImmutable() {
        return Collections.unmodifiableMap(new HashMap<>(map));
    }
}
