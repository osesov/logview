package app;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.scene.control.Label;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JsonLineReader {

    private record FileChunk(Path path, int fileId, long fileOffset, long size, MappedByteBuffer buffer) {}

    private static final int MAX_CHUNK_SIZE = Integer.MAX_VALUE - 8;
    private static final int MAX_PAGE_SIZE = 65_536;

    private final Map<String, Integer> fileIndexMap = new HashMap<>();
    private final List<FileChunk> chunks = new ArrayList<>();
    private final List<LineBounds> lines = new ArrayList<>();
    private final Map<LineBounds, String> cache = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
    private long rowIndex = 0;
    private int fileIndex = 0;
    private TableViewController tableController;
    private FileListController fileListController;
    private Label statusBar;

    JsonLineReader(TableViewController tableController, Label statusBar, FileListController fileListController) {
        this.tableController = tableController;
        this.fileListController = fileListController;
        this.statusBar = statusBar;
    }

    public void openFiles(List<Path> files) throws IOException {
        chunks.clear();
        lines.clear();
        cache.clear();
        rowIndex = 0;
        fileIndex = 0;

        for (Path path : files) {
            if (!Files.exists(path)) {
                throw new IOException("File not found: " + path);
            }

            addFile(path);
        }
    }

    public void removeFile(String fileName) {
        Integer fileId = fileIndexMap.get(fileName);
        if (fileId == null) {
            return; // file not found
        }

        lines.removeIf(line -> line.fileId() == fileId);
        chunks.removeIf(chunk -> chunk.fileId() == fileId);
        tableController.removeFile(fileName, fileId);
        fileIndexMap.remove(fileName);
    }

    public void addFile(Path path)
    {
        int chunkIndex = chunks.size();
        String fileName = path.toString();

        if (fileIndexMap.containsKey(path.toString())) {
            return; // already loaded
        } else {
            fileIndexMap.put(fileName, fileIndex);
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            long offset = 0;
            int blockOffset = 0;
            fileListController.addFile(path.toString(), fileIndex);
            while (offset < size) {
                long remaining = size - offset;
                long mapSize = Math.min(remaining, MAX_CHUNK_SIZE);
                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, offset, mapSize);
                chunks.add(new FileChunk(path, fileIndex, offset, mapSize, buffer));

                int len = buffer.limit();
                ScanChunk consumed = scanChunk(fileName, fileIndex, buffer, blockOffset, chunkIndex, rowIndex);
                if (consumed.pos < len) { // incomplete, overlap buffers
                    offset += consumed.pos % MAX_PAGE_SIZE * MAX_PAGE_SIZE;
                    blockOffset = consumed.pos % MAX_PAGE_SIZE;
                }
                else {
                    offset += consumed.pos;
                    blockOffset = 0;
                }

                rowIndex = consumed.rowIndex;
                chunkIndex++;
            }

            fileIndex++;
        }

        catch (IOException e) {
            throw new RuntimeException("Error reading file: " + path, e);
        }

        this.statusBar.setText("Loaded " + fileIndex + " files, " + rowIndex + " lines");
    }

    private record ScanChunk(int pos, long rowIndex) {}

    private ScanChunk scanChunk(String fileName, int fileIndex, ByteBuffer buffer, int blockOffset, int chunkIndex, long rowIndex) {
        JsonValueScanner scanner = new JsonValueScanner();
        int pos = blockOffset;
        while (pos < buffer.limit()) {
            Optional<int[]> match = scanner.nextValue(buffer, pos);
            if (match.isEmpty()) break;

            int start = match.get()[0];
            int end = match.get()[1];

            if (start >= end)
                break;
            LineBounds line = new LineBounds(fileName, fileIndex, chunkIndex, start, end, rowIndex++);
            pos = end;

            lines.add(line);
            this.tableController.addObject(line);
        }

        return new ScanChunk(pos, rowIndex);
    }

    public int getLineCount() {
        return lines.size();
    }

    public LineBounds getBounds(int rowIndex) {
        return lines.get(rowIndex);
    }

    public String getString(LineBounds b) {
        return cache.computeIfAbsent(b, k -> {
            FileChunk chunk = chunks.get(b.chunkIndex());
            ByteBuffer buffer = chunk.buffer().duplicate();
            buffer.position(b.start());
            byte[] data = new byte[b.end() - b.start()];
            buffer.get(data);
            return new String(data, StandardCharsets.UTF_8);
        });
    }

    public Map<String, Object> parse(LineBounds b) {
        try {
            return mapper.readValue(getString(b), Map.class);
        } catch (Exception e) {
            return Map.of("error", "Invalid JSON: " + e.getMessage());
        }
    }

    public List<LineBounds> getAllBounds() {
        return lines;
    }

}
