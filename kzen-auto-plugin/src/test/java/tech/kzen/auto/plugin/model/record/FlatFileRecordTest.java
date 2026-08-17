package tech.kzen.auto.plugin.model.record;


import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Characterization suite for the pooled, mutable record used on the report parse hot path.
 * Assertions pin observed behaviour, so reuse-cycle and cache-invalidation regressions surface here.
 */
class FlatFileRecordTest {
    private final long[] i128 = new long[2];

    private static final long positiveZeroBits = Double.doubleToRawLongBits(0.0);


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void ofExposesValuesByIndexAndAsList() {
        FlatFileRecord record = FlatFileRecord.of("a", "bb", "ccc");

        assertEquals(3, record.fieldCount());
        assertEquals(6, record.fieldContentLength());
        assertEquals("a", record.getString(0));
        assertEquals("bb", record.getString(1));
        assertEquals("ccc", record.getString(2));
        assertEquals(List.of("a", "bb", "ccc"), record.toList());
    }


    @Test
    void contentStartAndEndDelimitEachField() {
        FlatFileRecord record = FlatFileRecord.of("ab", "cde");

        assertEquals(0, record.contentStart(0));
        assertEquals(2, record.contentEnd(0));
        assertEquals(2, record.contentStart(1));
        assertEquals(5, record.contentEnd(1));
    }


    @Test
    void accessBeyondFieldCountIsUnchecked() {
        FlatFileRecord record = FlatFileRecord.of("ab", "cde");

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> record.getString(2));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> record.contentEnd(2));
    }


    @Test
    void ofSingleWrapsOneFieldFromCharWindow() {
        FlatFileRecord record = FlatFileRecord.ofSingle("xxhelloyy".toCharArray(), 2, 5);

        assertEquals(1, record.fieldCount());
        assertEquals("hello", record.getString(0));
    }


    @Test
    void emptinessDependsOnHowTheBlankRecordWasBuilt() {
        // ofSingle marks the record non-empty, of(...) does not — so a blank single field reads
        // as empty through one factory and as present through the other.
        assertTrue(FlatFileRecord.of().isEmpty());
        assertTrue(FlatFileRecord.of("").isEmpty());
        assertFalse(FlatFileRecord.ofSingle(new char[0], 0, 0).isEmpty());
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void csvQuotesOnlyFieldsHoldingSeparatorsQuotesOrNewlines() {
        FlatFileRecord record = FlatFileRecord.of("a,b", "c\"d", "e\nf", "plain");

        assertEquals("\"a,b\",\"c\"\"d\",\"e\nf\",plain", record.toCsv());
    }


    @Test
    void toStringRendersTheCsvForm() {
        FlatFileRecord record = FlatFileRecord.of("a", "b");

        assertEquals(record.toCsv(), record.toString());
    }


    @Test
    void csvOfARecordMarkedNonEmptyWithNoContentIsAQuotedEmptyField() {
        FlatFileRecord record = new FlatFileRecord();
        record.indicateNonEmpty();
        record.commitField();

        assertEquals("\"\"", record.toCsv());
        assertEquals("", FlatFileRecord.of("").toCsv());
    }


    @Test
    void writeCsvFieldEmitsASingleQuotedField() throws Exception {
        FlatFileRecord record = FlatFileRecord.of("x,y", "z");
        StringWriter writer = new StringWriter();

        record.writeCsvField(0, writer);

        assertEquals("\"x,y\"", writer.toString());
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void cachedDoubleIgnoresSurroundingSpacesAndYieldsNanForNonNumbers() {
        FlatFileRecord record = FlatFileRecord.of("1", "  2  ", "x", "");

        assertEquals(1.0, record.cachedDoubleOrNan(0, i128));
        assertEquals(2.0, record.cachedDoubleOrNan(1, i128));
        assertEquals(Double.NaN, record.cachedDoubleOrNan(2, i128));
        assertEquals(Double.NaN, record.cachedDoubleOrNan(3, i128));
    }


    @Test
    void negativeZeroFieldCachesAsPositiveZeroSoTheMissingSentinelStaysUnambiguous() {
        // doubleCacheMissing is -0.0, which only works as a sentinel because the parser
        // normalizes a parsed -0 to +0 — otherwise a real "-0" field would read as uncached forever.
        FlatFileRecord record = FlatFileRecord.of("-0", "-0.0");

        assertEquals(positiveZeroBits, Double.doubleToRawLongBits(record.cachedDoubleOrNan(0, i128)));
        assertEquals(positiveZeroBits, Double.doubleToRawLongBits(record.cachedDoubleOrNan(1, i128)));
        assertTrue(record.isCached(0));
        assertTrue(record.isCached(1));
    }


    @Test
    void fieldIsUncachedUntilFirstNumericAccess() {
        FlatFileRecord record = new FlatFileRecord();
        record.add("abc");

        assertFalse(record.isCached(0));
        assertEquals(Double.NaN, record.cachedDoubleOrNan(0, i128));
        assertTrue(record.isCached(0));
    }


    @Test
    void ofPopulatesCachesEagerly() {
        assertTrue(FlatFileRecord.of("1").isCached(0));
    }


    @Test
    void cachedHashDependsOnlyOnFieldContent() {
        FlatFileRecord twoFields = FlatFileRecord.of("x", "abc");
        FlatFileRecord oneField = FlatFileRecord.of("abc");

        assertEquals(oneField.cachedHash(0, i128), twoFields.cachedHash(1, i128));
        assertNotEquals(twoFields.cachedHash(0, i128), twoFields.cachedHash(1, i128));
        assertEquals(0L, FlatFileRecord.of("").cachedHash(0, i128));
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void clearResetsCountsAndCacheForReuse() {
        FlatFileRecord record = new FlatFileRecord();
        record.add("abc");
        record.add("12");
        assertEquals(12.0, record.cachedDoubleOrNan(1, i128));

        record.clear();

        assertEquals(0, record.fieldCount());
        assertEquals(0, record.fieldContentLength());
        assertTrue(record.isEmpty());

        record.add("zz");

        assertEquals(List.of("zz"), record.toList());
        assertFalse(record.isCached(0));
        assertEquals(Double.NaN, record.cachedDoubleOrNan(0, i128));
    }


    @Test
    void clearCacheKeepsContentAndForcesReparse() {
        FlatFileRecord record = FlatFileRecord.of("77");
        assertTrue(record.isCached(0));

        record.clearCache();

        assertFalse(record.isCached(0));
        assertEquals("77", record.getString(0));
        assertEquals(77.0, record.cachedDoubleOrNan(0, i128));
    }


    @Test
    void clearResetsBothContentAndParsedValues() {
        FlatFileRecord record = new FlatFileRecord();
        record.add("7");
        assertEquals(7.0, record.cachedDoubleOrNan(0, i128));

        record.clear();
        record.add("9");

        assertEquals("9", record.getString(0));
        assertEquals(9.0, record.cachedDoubleOrNan(0, i128));
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void copyReplacesContentAndInvalidatesCache() {
        FlatFileRecord target = new FlatFileRecord();
        target.add("longer-than-source");
        assertEquals(Double.NaN, target.cachedDoubleOrNan(0, i128));

        target.copy(FlatFileRecord.of("66"));

        assertEquals(List.of("66"), target.toList());
        assertFalse(target.isCached(0));
        assertEquals(66.0, target.cachedDoubleOrNan(0, i128));
    }


    @Test
    void exchangeSwapsContentAndCachesBetweenRecords() {
        FlatFileRecord first = FlatFileRecord.of("11");
        FlatFileRecord second = FlatFileRecord.of("22", "33");

        first.exchange(second);

        assertEquals(List.of("22", "33"), first.toList());
        assertEquals(List.of("11"), second.toList());
        assertEquals(22.0, first.cachedDoubleOrNan(0, i128));
        assertEquals(11.0, second.cachedDoubleOrNan(0, i128));
    }


    @Test
    void cloneAliasesTheSourceBackingArrays() {
        FlatFileRecord source = FlatFileRecord.of("ab", "cd");
        FlatFileRecord alias = new FlatFileRecord();

        alias.clone(source);

        assertSame(source.fieldContentsUnsafe(), alias.fieldContentsUnsafe());

        alias.clear();
        alias.add("zz");

        assertEquals(List.of("zz"), alias.toList());
        assertEquals(List.of("zz", "cd"), source.toList());
    }


    @Test
    void prototypeProducesAnIndependentCopy() {
        FlatFileRecord source = FlatFileRecord.of("ab", "cd");

        FlatFileRecord prototype = source.prototype();
        assertEquals(List.of("ab", "cd"), prototype.toList());

        prototype.clear();
        prototype.add("yy");

        assertEquals(List.of("yy"), prototype.toList());
        assertEquals(List.of("ab", "cd"), source.toList());
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void growthKeepsEveryFieldIntactAcrossManyAppends() {
        int fieldCount = 50;
        FlatFileRecord record = new FlatFileRecord(0, 0);

        for (int i = 0; i < fieldCount; i++) {
            record.add("f" + i);
        }

        assertEquals(fieldCount, record.fieldCount());
        for (int i = 0; i < fieldCount; i++) {
            assertEquals("f" + i, record.getString(i));
        }
    }


    @Test
    void growToRaisesCapacityWithoutEverShrinkingIt() {
        FlatFileRecord record = new FlatFileRecord(0, 0);

        record.growTo(100, 10);
        assertEquals(100, record.fieldContentsUnsafe().length);
        assertEquals(10, record.fieldEndsUnsafe().length);

        record.growTo(5, 2);
        assertEquals(100, record.fieldContentsUnsafe().length);
        assertEquals(10, record.fieldEndsUnsafe().length);
    }


    @Test
    void growByReservesOneFieldSlotBeyondTheRequest() {
        FlatFileRecord record = new FlatFileRecord(0, 0);
        record.add("a");

        record.growBy(3, 1);

        assertEquals(4, record.fieldContentsUnsafe().length);
        assertEquals(3, record.fieldEndsUnsafe().length);
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void addLongRendersBoundaryValues() {
        FlatFileRecord record = new FlatFileRecord();

        record.add(0L);
        record.add(-1L);
        record.add(Long.MAX_VALUE);
        record.add(Long.MIN_VALUE);

        assertEquals(
                List.of("0", "-1", "9223372036854775807", "-9223372036854775808"),
                record.toList());
    }


    @Test
    void addDoubleZeroPadsTheFractionToTheRequestedPlaces() {
        FlatFileRecord record = new FlatFileRecord();

        record.add(1.5, 2);
        record.add(-1.5, 2);
        record.add(0.05, 2);
        record.add(-0.05, 2);
        record.add(0.0, 3);
        record.add(123.456, 3);

        assertEquals(
                List.of("1.50", "-1.50", "0.05", "-0.05", "0.000", "123.456"),
                record.toList());
    }


    @Test
    void addDoubleCarriesRoundedFractionIntoTheWholePart() {
        FlatFileRecord record = new FlatFileRecord();

        record.add(1.999, 2);
        record.add(-1.999, 2);
        record.add(0.999, 2);
        record.add(9.999, 2);
        record.add(99.995, 2);

        assertEquals(
                List.of("2.00", "-2.00", "1.00", "10.00", "100.00"),
                record.toList());
    }


    @Test
    void addDoubleWithZeroPlacesRoundsHalfTowardsPositiveInfinity() {
        FlatFileRecord record = new FlatFileRecord();

        record.add(1.5, 0);
        record.add(2.5, 0);
        record.add(3.5, 0);
        record.add(-2.5, 0);

        assertEquals(List.of("2", "3", "4", "-2"), record.toList());
    }


    @Test
    void addDoubleKeepsTheSignWhenTheMagnitudeRoundsToZero() {
        FlatFileRecord record = new FlatFileRecord();

        record.add(-0.001, 2);
        record.add(-0.0, 3);

        assertEquals(List.of("-0.00", "0.000"), record.toList());
    }


    @Test
    void addDoubleAcceptsUpToSeventeenDecimalPlaces() {
        FlatFileRecord record = new FlatFileRecord();
        int maxDecimalPlaces = 17;

        record.add(1.5, maxDecimalPlaces);

        assertEquals("1.50000000000000000", record.getString(0));
        assertThrows(
                ArrayIndexOutOfBoundsException.class,
                () -> new FlatFileRecord().add(1.5, maxDecimalPlaces + 1));
        assertThrows(
                ArrayIndexOutOfBoundsException.class,
                () -> new FlatFileRecord().add(1.5, -1));
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    void charactersAccumulateUntilCommitDelimitsAField() {
        FlatFileRecord record = new FlatFileRecord();

        record.addToField('a');
        record.addToField("bc".toCharArray(), 0, 2);
        record.commitField();
        record.commitField();
        record.add("xyz".toCharArray(), 1, 2);

        assertEquals(List.of("abc", "", "yz"), record.toList());
    }


    @Test
    void unsafeAppendersFillPreallocatedCapacity() {
        int contentCapacity = 16;
        int fieldCapacity = 4;
        FlatFileRecord record = new FlatFileRecord(contentCapacity, fieldCapacity);

        record.addToFieldUnsafe('a');
        record.addToFieldUnsafe("bc".toCharArray(), 0, 2);
        record.commitFieldUnsafe();
        record.addToFieldAndCommitUnsafe("de".toCharArray(), 0, 2);

        assertEquals(List.of("abc", "de"), record.toList());
        assertEquals(contentCapacity, record.fieldContentsUnsafe().length);
    }


    @Test
    void countAndLengthCanBeSetDirectlyForBulkFilledBuffers() {
        FlatFileRecord record = new FlatFileRecord(8, 2);
        System.arraycopy("abcd".toCharArray(), 0, record.fieldContentsUnsafe(), 0, 4);
        record.fieldEndsUnsafe()[0] = 2;
        record.fieldEndsUnsafe()[1] = 4;

        record.setCountAndLengthUnsafe(2, 4);

        assertEquals(List.of("ab", "cd"), record.toList());
    }
}
