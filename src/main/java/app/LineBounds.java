package app;

public record LineBounds(int fileId, int chunkIndex, int start, int end, long objIndex) {}

// public class LineBounds {
//     private int start;
//     private int end;
//     private int index;

//     public LineBounds(int start, int end, int index) {
//         this.start = start;
//         this.end = end;
//         this.index = index;
//     }

//     public int getStart() {
//         return start;
//     }

//     public int getEnd() {
//         return end;
//     }

//     public int getIndex() {
//         return index;
//     }

//     @Override
//     public String toString() {
//         return "LineBounds{" +
//                 "start=" + start +
//                 ", end=" + end +
//                 ", index=" + index +
//                 '}';
//     }
// }
