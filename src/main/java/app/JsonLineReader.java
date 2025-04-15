package app;


import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import javafx.scene.control.Label;

public class JsonLineReader {
    private Path path;
    private MappedByteBuffer buffer;

    public JsonLineReader(Path path) {
        this.path = path;
    }

    public void readLines(TableViewController tableController, Label statusBar)
    {
        try {
            long size = Files.size(path);

            buffer = FileChannel.open(path, StandardOpenOption.READ)
                .map(FileChannel.MapMode.READ_ONLY, 0, size);

            JsonValueScanner scanner = new JsonValueScanner();
            int pos = 0;
            long numRows = 0;

            tableController.reset(this);
            while (pos < buffer.limit()) {
                Optional<int[]> bounds = scanner.nextValue(buffer, pos);
                if (bounds.isEmpty())
                    break;

                int start = bounds.get()[0];
                int end = bounds.get()[1];

                // System.out.println("Found JSON: " + value);
                tableController.addObject(new LineBounds(start, end));
                pos = end;
                numRows++;
            }

            statusBar.setText("Loaded " + numRows + " entries from: " + path.getFileName());
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public String getString(LineBounds bounds) {
        return JsonValueScanner.extract(buffer, bounds);
    }

}
