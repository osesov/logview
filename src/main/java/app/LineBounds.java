package app;

public record LineBounds(String fileName, int fileId, int chunkIndex, int start, int end, long objIndex) {}
