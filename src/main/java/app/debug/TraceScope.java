package app.debug;

import java.util.Map;
import java.util.function.Supplier;

public class TraceScope implements AutoCloseable {
    private final String name;

    public TraceScope(String name, Map<String, String> args) {
        this.name = name;
        TraceLogger.begin(name, args);
    }

    @Override
    public void close() {
        TraceLogger.end(name, Map.of());
    }

    public static <T> T trace(String name, Supplier<T> supplier, Map<String, String> args) {
        try (TraceScope ignored = new TraceScope(name, args)) {
            return supplier.get();
        }
    }
}
