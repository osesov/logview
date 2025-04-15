package app;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javafx.scene.control.Label;

public class JsonLineReader {
    private Path path;
    private MappedByteBuffer buffer;
    private final Map<Integer, String> lineCache;
    // TODO: implement chunks to map files larger than 2GB
    // chunks should be aligned to JSON object to simplify reading
    //

    public JsonLineReader(Path path) {
        this.path = path;

        // use Map<Integer, SoftReference<String>> instead?
        lineCache = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                return size() > 10_000;
            }
        };

    }

    public void readLines(TableViewController tableController, Label statusBar) {
        try {
            long size = Files.size(path);

            buffer = FileChannel.open(path, StandardOpenOption.READ)
                    .map(FileChannel.MapMode.READ_ONLY, 0, size);

            JsonValueScanner scanner = new JsonValueScanner();
            int pos = 0;
            long rowIndex = 0;

            tableController.reset(this);
            while (pos < buffer.limit()) {
                Optional<int[]> bounds = scanner.nextValue(buffer, pos);
                if (bounds.isEmpty())
                    break;

                int start = bounds.get()[0];
                int end = bounds.get()[1];

                // System.out.println("Found JSON: " + value);
                tableController.addObject(new LineBounds(start, end, (int)rowIndex));
                pos = end;
                rowIndex++;
            }

            statusBar.setText("Loaded " + rowIndex + " entries from: " + path.getFileName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getString(LineBounds bounds) {
        return lineCache.computeIfAbsent(bounds.getStart(), k -> {
            return JsonValueScanner.extract(buffer, bounds);
        });
    }
}
