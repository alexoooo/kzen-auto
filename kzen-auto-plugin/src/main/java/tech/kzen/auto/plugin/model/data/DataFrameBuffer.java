package tech.kzen.auto.plugin.model.data;


import java.util.Arrays;


public class DataFrameBuffer {
    //-----------------------------------------------------------------------------------------------------------------
    private static final int initialSize = 0;


    //-----------------------------------------------------------------------------------------------------------------
    public int[] offsets = new int[initialSize];
    public int[] lengths = new int[initialSize];
    public boolean partialLast;
    public int count;


    //-----------------------------------------------------------------------------------------------------------------
    // NB: reads as "safe to enter the drain loop", not "holds a complete frame" — an empty buffer answers true.
    //  DataFrameFeeder relies on that: at count == 0 it falls through to a drain range of 0..-1 (a no-op), whereas
    //  answering false would route it into partialInput.addFrame(buffer, 0) against an unwritten lengths[0].
    public boolean hasFull() {
        return count > 1 || ! partialLast;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void clear() {
        count = 0;
        partialLast = false;
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
}
