package app;

public class LineBounds {
    private int start;
    private int end;

    public LineBounds(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return "LineBounds{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }
}
