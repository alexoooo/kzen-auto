package tech.kzen.auto.plugin.model.data;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Characterization suite for the frame index that {@code DataFrameFeeder} drains.
 */
class DataFrameBufferTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void freshBufferHoldsNoFramesAndAllocatesNothing() {
        DataFrameBuffer buffer = new DataFrameBuffer();

        assertEquals(0, buffer.count);
        assertFalse(buffer.partialLast);
        assertEquals(0, buffer.offsets.length);
        assertEquals(0, buffer.lengths.length);
    }


    @Test
    void addRecordsOffsetsAndLengthsInInsertionOrder() {
        DataFrameBuffer buffer = new DataFrameBuffer();

        buffer.add(0, 3);
        buffer.add(3, 5);

        assertEquals(2, buffer.count);
        assertEquals(0, buffer.offsets[0]);
        assertEquals(3, buffer.lengths[0]);
        assertEquals(3, buffer.offsets[1]);
        assertEquals(5, buffer.lengths[1]);
    }


    @Test
    void capacityGrowsByAFifthWithAMinimumOfOneSlot() {
        DataFrameBuffer buffer = new DataFrameBuffer();
        int growthThreshold = 10;

        for (int i = 0; i < growthThreshold; i++) {
            buffer.add(i, 1);
            assertEquals(i + 1, buffer.offsets.length, "at frame " + i);
        }

        buffer.add(growthThreshold, 1);

        assertEquals(12, buffer.offsets.length);
        assertEquals(12, buffer.lengths.length);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void singleFrameCountsAsFullUnlessMarkedPartial() {
        DataFrameBuffer buffer = new DataFrameBuffer();
        buffer.add(0, 5);

        assertTrue(buffer.hasFull());

        buffer.setPartialLast();
        assertFalse(buffer.hasFull());

        buffer.clearPartialLast();
        assertTrue(buffer.hasFull());
    }


    @Test
    void severalFramesCountAsFullEvenWhenTheLastIsPartial() {
        DataFrameBuffer buffer = new DataFrameBuffer();
        buffer.add(0, 5);
        buffer.add(5, 2);
        buffer.setPartialLast();

        assertTrue(buffer.hasFull());
    }


    @Test
    void emptyBufferReportsHasFullUnlessMarkedPartial() {
        // Suspected defect, pinned as-is: with no frames at all hasFull() is true, because the
        // predicate only tests the partial-last flag once the count is at most one. DataFrameFeeder
        // survives it because the resulting drain range is empty.
        DataFrameBuffer buffer = new DataFrameBuffer();

        assertTrue(buffer.hasFull());

        buffer.setPartialLast();
        assertFalse(buffer.hasFull());
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void clearResetsTheCursorAndFlagButRetainsCapacity() {
        DataFrameBuffer buffer = new DataFrameBuffer();
        buffer.add(0, 3);
        buffer.add(3, 3);
        buffer.setPartialLast();

        buffer.clear();

        assertEquals(0, buffer.count);
        assertFalse(buffer.partialLast);
        assertEquals(2, buffer.offsets.length);

        buffer.add(7, 4);

        assertEquals(1, buffer.count);
        assertEquals(7, buffer.offsets[0]);
        assertEquals(4, buffer.lengths[0]);
    }
}
