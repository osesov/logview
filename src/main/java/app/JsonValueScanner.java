package app;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class JsonValueScanner {

    public Optional<int[]> nextValue(ByteBuffer buffer, int start) {
        int len = buffer.limit();
        int pos = skipWhitespace(buffer, start, len);
        if (pos >= len) return Optional.empty();

        char firstChar = (char) buffer.get(pos);
        int end = switch (firstChar) {
            case '{' -> findBalanced(buffer, pos, '{', '}');
            case '[' -> findBalanced(buffer, pos, '[', ']');
            case '"' -> findStringEnd(buffer, pos);
            default  -> findPrimitiveEnd(buffer, pos);
        };

        if (end < 0) return Optional.empty(); // incomplete or malformed
        return Optional.of(new int[]{pos, end});
    }

    private int skipWhitespace(ByteBuffer buffer, int pos, int len) {
        while (pos < len) {
            char c = (char) buffer.get(pos);
            if (!Character.isWhitespace(c)) break;
            pos++;
        }
        return pos;
    }

    private int findBalanced(ByteBuffer buf, int pos, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = pos; i < buf.limit(); i++) {
            char c = (char) buf.get(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return i + 1;
                    }
                }
            }
        }
        return -1; // not closed
    }

    private int findStringEnd(ByteBuffer buf, int pos) {
        boolean escape = false;
        for (int i = pos + 1; i < buf.limit(); i++) {
            char c = (char) buf.get(i);
            if (escape) {
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return i + 1;
            }
        }
        return -1; // not closed
    }

    private int findPrimitiveEnd(ByteBuffer buf, int pos) {
        for (int i = pos; i < buf.limit(); i++) {
            char c = (char) buf.get(i);
            if (c == ',' || c == ']' || c == '}' || Character.isWhitespace(c)) {
                return i;
            }
        }
        return buf.limit(); // till end
    }

    // Debug helper
    public static String extract(ByteBuffer buffer, int start, int end) {
        byte[] data = new byte[end - start];
        buffer.position(start);
        buffer.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static String extract(ByteBuffer buffer, LineBounds bounds) {
        if (bounds == null) {
            return "";
        }

        byte[] data = new byte[bounds.getEnd() - bounds.getStart()];
        buffer.position(bounds.getStart());
        buffer.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

}
