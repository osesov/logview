package app.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Supplier;

public class TraceLogger {
    private static final List<TraceEvent> events = Collections.synchronizedList(new ArrayList<>());
    private static final long startTime = System.nanoTime();
    private static final int pid = getProcessId();

    private static long nowMicro() {
        return (System.nanoTime() - startTime) / 1000;
    }

    private static int getProcessId() {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        return Integer.parseInt(jvmName.split("@")[0]);
    }

    public static void instant(String name, String category, Map<String, String> args) {
        TraceEvent e = new TraceEvent(name, "i", nowMicro(), pid, (int) Thread.currentThread().getId(), args);
        e.cat = category;
        events.add(e);
    }

    public static void begin(String name, Map<String, String> args) {
        events.add(new TraceEvent(name, "B", nowMicro(), pid, (int) Thread.currentThread().getId(), args));
    }

    public static void end(String name, Map<String, String> args) {
        events.add(new TraceEvent(name, "E", nowMicro(), pid, (int) Thread.currentThread().getId(), args));
    }

    public static void complete(String name, long durationMicros, Map<String, String> args) {
        events.add(new TraceEvent(name, "X", nowMicro(), pid, (int) Thread.currentThread().getId(), args, durationMicros));
    }

    public static void metadata(String name, String value) {
        Map<String, String> args = new HashMap<>();
        args.put("name", value);
        events.add(new TraceEvent(name, "M", nowMicro(), pid, (int) Thread.currentThread().getId(), args));
    }

    public static <T> T trace(String name, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            // TraceLogger.begin(name, Map.of());
            return action.get();
        } finally {
            long duration = (System.nanoTime() - start) / 1000;
            TraceLogger.complete(name, duration, Map.of());
        }
    }

    public static void trace(String name, Runnable block) {
        TraceLogger.begin(name, Map.of());
        try {
            block.run();
        } finally {
            TraceLogger.end(name, Map.of());
        }
    }

    public static void traceComplete(String name, Runnable block) {
        long start = System.nanoTime();
        try {
            block.run();
        } finally {
            long durationMicros = (System.nanoTime() - start) / 1000;
            TraceLogger.complete(name, durationMicros, Map.of());
        }
    }

    public static void save(String filename)
    {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectWriter writer = mapper.writer();

        Map<String, Object> output = new HashMap<>();
        output.put("traceEvents", events);
        try {
            writer.writeValue(new File(filename), output);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}
