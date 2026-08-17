package tech.kzen.auto.plugin.model.record;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Characterization suite for the flyweight view over a {@link FlatFileRecord} field.
 * Assertions pin observed behaviour, including the states in which the flyweight is not usable.
 */
class FlatFileRecordFieldTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void standaloneExposesItsValueAsACharSequence() {
        FlatFileRecordField field = FlatFileRecordField.standalone("abc");

        assertEquals(3, field.length());
        assertEquals('a', field.charAt(0));
        assertEquals('c', field.charAt(2));
        assertEquals("abc", field.toString());
    }


    @Test
    void emptyConstantIsBothEmptyAndBlank() {
        assertEquals(0, FlatFileRecordField.empty.length());
        assertTrue(FlatFileRecordField.empty.isEmpty());
        assertTrue(FlatFileRecordField.empty.isBlankOrEmpty());
    }


    @Test
    void allSpacesFieldIsBlankButNotEmpty() {
        FlatFileRecordField field = FlatFileRecordField.standalone("   ");

        assertFalse(field.isEmpty());
        assertTrue(field.isBlankOrEmpty());
        assertFalse(FlatFileRecordField.standalone(" a ").isBlankOrEmpty());
    }


    @Test
    void subSequenceIsNotSupported() {
        FlatFileRecordField field = FlatFileRecordField.standalone("abc");

        assertThrows(UnsupportedOperationException.class, () -> field.subSequence(0, 1));
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void selectHostFieldWindowsOntoTheChosenRecordField() {
        FlatFileRecord host = FlatFileRecord.of("aa", "bbb");
        FlatFileRecordField field = new FlatFileRecordField();

        field.selectHostField(host, 1);
        assertEquals("bbb", field.toString());

        field.selectField(0);
        assertEquals("aa", field.toString());
    }


    @Test
    void selectHostValueWindowsOntoAnArbitraryRange() {
        FlatFileRecord host = FlatFileRecord.of("aa", "bcd");
        FlatFileRecordField field = new FlatFileRecordField();

        field.selectHostValue(host, 1, 2, 2);

        assertEquals("bc", field.toString());
        assertEquals(2, field.length());
    }


    @Test
    void selectHostAloneLeavesTheWindowUnset() {
        // selectHost only rebinds the record; length stays at the -1 "no field chosen yet" marker
        // until one of the selectField overloads runs.
        FlatFileRecord host = FlatFileRecord.of("aa", "bbb");
        FlatFileRecordField field = new FlatFileRecordField();

        field.selectHost(host);

        assertEquals(-1, field.length());
        assertFalse(field.isEmpty());
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void toDoubleOrNanReadsThroughTheHostCacheIgnoringSurroundingSpaces() {
        FlatFileRecord host = FlatFileRecord.of("aa", " 3.5 ", "x");
        FlatFileRecordField field = new FlatFileRecordField();

        field.selectHostField(host, 1);
        assertEquals(3.5, field.toDoubleOrNan());

        field.selectField(2);
        assertEquals(Double.NaN, field.toDoubleOrNan());
    }


    @Test
    void goodHashIsStableForEqualContentAndDiffersForOtherContent() {
        FlatFileRecordField first = FlatFileRecordField.standalone("abc");
        FlatFileRecordField same = FlatFileRecordField.standalone("abc");
        FlatFileRecordField other = FlatFileRecordField.standalone("abd");

        assertEquals(first.goodHash(), same.goodHash());
        assertNotEquals(first.goodHash(), other.goodHash());
        assertEquals((int) first.goodHash(), first.hashCode());
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void equalsComparesContentAcrossDistinctHosts() {
        FlatFileRecordField first = FlatFileRecordField.standalone("abc");
        FlatFileRecordField same = FlatFileRecordField.standalone("abc");
        FlatFileRecordField shorter = FlatFileRecordField.standalone("ab");
        FlatFileRecordField other = FlatFileRecordField.standalone("abd");

        assertEquals(first, first);
        assertEquals(first, same);
        assertNotEquals(first, shorter);
        assertNotEquals(first, other);
        assertEquals(FlatFileRecordField.empty, FlatFileRecordField.standalone(""));
    }


    @Test
    void equalsAgainstAnUnrelatedTypeIsFalse() {
        assertFalse(FlatFileRecordField.standalone("abc").equals("abc"));
    }


    @Test
    void equalsAgainstNullIsFalse() {
        FlatFileRecordField field = FlatFileRecordField.standalone("abc");

        assertFalse(field.equals(null));
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void detachCopiesTheContentAndKeepsNumericAccessorsUsable() {
        FlatFileRecord host = FlatFileRecord.of("12", "bb");
        FlatFileRecordField selected = new FlatFileRecordField();
        selected.selectHostField(host, 0);

        FlatFileRecordField detached = selected.detach();

        assertEquals("12", detached.toString());
        assertEquals(2, detached.length());

        // The detached flyweight owns a single-field record, so its host-cache lookups resolve to index 0.
        assertEquals(12.0, detached.toDoubleOrNan());
        assertEquals(FlatFileRecordField.standalone("12").goodHash(), detached.goodHash());
        assertTrue(detached.equals(FlatFileRecordField.standalone("12")));
    }


    @Test
    void detachSurvivesTheHostBeingCleared() {
        FlatFileRecord host = FlatFileRecord.of("aa", "bb");
        FlatFileRecordField selected = new FlatFileRecordField();
        selected.selectHostField(host, 0);

        FlatFileRecordField detached = selected.detach();
        host.clear();

        assertEquals("aa", detached.toString());
    }
}
