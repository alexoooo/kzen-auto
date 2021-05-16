package tech.kzen.auto.plugin.model;


import java.util.Arrays;


public class DataFrameBuffer {
    //-----------------------------------------------------------------------------------------------------------------
    private static final int initialSize = 0;


    //-----------------------------------------------------------------------------------------------------------------
    public int[] offsets = new int[initialSize];
    public int[] lengths = new int[initialSize];
    public boolean partialLast;
    public int count;
//    public boolean endOfData;


    //-----------------------------------------------------------------------------------------------------------------
//    public int offset(int index) {
//        return frames[index * 2];
//    }
//
//
//    public int length(int index) {
//        return frames[index * 2 + 1];
//    }


    public boolean hasFull() {
        return count > 1 || ! partialLast;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void clear() {
        count = 0;
        partialLast = false;
//        endOfData = false;
    }


    public void add(int offset, int length) {
        if (offsets.length <= count) {
            int newSize = count + Math.max(1, (int) (count * 0.2));
            offsets = Arrays.copyOf(offsets, newSize);
            lengths = Arrays.copyOf(lengths, newSize);
        }
        offsets[count] = offset;
        lengths[count] = length;
        count++;
    }


    public void setPartialLast() {
        partialLast = true;
    }


    public void clearPartialLast() {
        partialLast = false;
    }


//    public void setEndOfData() {
//        endOfData = true;
//    }
}
