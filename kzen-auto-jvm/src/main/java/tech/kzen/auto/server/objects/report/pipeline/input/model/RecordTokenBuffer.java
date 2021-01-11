package tech.kzen.auto.server.objects.report.pipeline.input.model;


import java.util.Arrays;


public class RecordTokenBuffer
{
    //-----------------------------------------------------------------------------------------------------------------
    // triplets: offset, length, field count
    public int[] tokens = new int[128 * 3];
//    public boolean partialFirst;
    public boolean partialLast;
    public int count;


    //-----------------------------------------------------------------------------------------------------------------
    public int offset(int index) {
        return tokens[index * 3];
    }


    public int length(int index) {
        return tokens[index * 3 + 1];
    }


    public int fieldCount(int index) {
        return tokens[index * 3 + 2];
    }


    public boolean hasFull() {
        return count > 1 || ! partialLast;
    }


    //-----------------------------------------------------------------------------------------------------------------
    public void clear() {
        count = 0;
//        partialFirst = false;
        partialLast = false;
    }


    public void add(int offset, int length, int fieldCount) {
        int start = count * 3;
        if (tokens.length < start + 3) {
            tokens = Arrays.copyOf(tokens, start + 3);
        }
        tokens[start] = offset;
        tokens[start + 1] = length;
        tokens[start + 2] = fieldCount;
        count++;
    }


//    public void setPartialFirst() {
//        partialFirst = true;
//    }


    public void setPartialLast() {
        partialLast = true;
    }

    public void clearPartialLast() {
        partialLast = false;
    }
}
