package tech.kzen.auto.common.data.read

import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.testDataPart
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class ReadSpecTest {
    @Test
    fun mapOrderDoesNotChangeResolvedIdentityButListOrderDoes() {
        val reader = ReaderCapabilityIdentity("example", "reader", "1")
        val forward = MapExecutionValue(linkedMapOf(
            "first" to TextExecutionValue("1"),
            "second" to TextExecutionValue("2")))
        val reverse = MapExecutionValue(linkedMapOf(
            "second" to TextExecutionValue("2"),
            "first" to TextExecutionValue("1")))

        assertEquals(
            ResolvedReadSpec(reader, listOf(ContentCodingSpec.identity), forward).digest(),
            ResolvedReadSpec(reader, listOf(ContentCodingSpec.identity), reverse).digest())
        assertNotEquals(
            ResolvedReadSpec(
                reader,
                listOf(ContentCodingSpec.identity, ContentCodingSpec.gzip),
                MapExecutionValue(emptyMap())).digest(),
            ResolvedReadSpec(
                reader,
                listOf(ContentCodingSpec.gzip, ContentCodingSpec.identity),
                MapExecutionValue(emptyMap())).digest())
    }

    @Test
    fun orderedEntryListAndAbsentNullRemainDistinct() {
        val first = ListExecutionValue(listOf(
            MapExecutionValue(mapOf("key" to TextExecutionValue("a"))),
            MapExecutionValue(mapOf("key" to TextExecutionValue("b")))))
        val reverse = ListExecutionValue(first.values.reversed())
        val absent = MapExecutionValue(emptyMap())
        val explicitNull = MapExecutionValue(mapOf("value" to NullExecutionValue))

        assertNotEquals(first.digest(), reverse.digest())
        assertNotEquals(absent.digest(), explicitNull.digest())
    }

    @Test
    fun partIdentityIncludesFingerprintAndResolvedRead() {
        val ref = DataRef(null, "input.csv", mapOf(
            DataRef.sizeKey to "10",
            DataRef.modifiedKey to "20"))
        val part = testDataPart(DataRole.main, ref).copy(expectedFingerprint =
            DataContentFingerprint("provider-etag", TextExecutionValue("etag-a")))
        val changedFingerprint = part.copy(expectedFingerprint = DataContentFingerprint(
            "test", TextExecutionValue("different")))
        val changedRead = part.copy(resolvedRead = part.resolvedRead.copy(
            reader = part.resolvedRead.reader.copy(name = "other")))

        assertNotEquals(part.digest(), changedFingerprint.digest())
        assertNotEquals(part.digest(), changedRead.digest())
        assertEquals(part, DataPart.ofExecutionValue(part.asExecutionValue()))
    }

    @Test
    fun partIdentitySeparatesEverySemanticDimension() {
        val ref = DataRef(
            tech.kzen.auto.common.data.model.DataSourceId("source-a"),
            "input.csv",
            mapOf(DataRef.sizeKey to "10", DataRef.modifiedKey to "20"))
        val fingerprint = DataContentFingerprint(
            "provider-etag",
            TextExecutionValue("etag-a"))
        val part = testDataPart(DataRole.main, ref).copy(expectedFingerprint = fingerprint)

        val variants = listOf(
            part.copy(role = DataRole("reference")),
            part.copy(ref = ref.copy(source = tech.kzen.auto.common.data.model.DataSourceId("source-b"))),
            part.copy(ref = ref.copy(id = "other.csv")),
            part.copy(ref = ref.copy(attributes = ref.attributes + (DataRef.sizeKey to "11"))),
            part.copy(expectedFingerprint = fingerprint.copy(identity = "other")),
            part.copy(expectedFingerprint = fingerprint.copy(data = TextExecutionValue("other"))),
            part.copy(resolvedRead = part.resolvedRead.copy(
                reader = part.resolvedRead.reader.copy(namespace = "other"))),
            part.copy(resolvedRead = part.resolvedRead.copy(
                reader = part.resolvedRead.reader.copy(name = "other"))),
            part.copy(resolvedRead = part.resolvedRead.copy(
                reader = part.resolvedRead.reader.copy(compatibility = "2"))),
            part.copy(resolvedRead = ResolvedReadSpec(
                part.resolvedRead.reader,
                listOf(ContentCodingSpec.gzip),
                part.resolvedRead.config)),
            part.copy(resolvedRead = ResolvedReadSpec(
                part.resolvedRead.reader,
                listOf(ContentCodingSpec("identity", TextExecutionValue("configured"))),
                part.resolvedRead.config)),
            part.copy(resolvedRead = ResolvedReadSpec(
                part.resolvedRead.reader,
                part.resolvedRead.contentCodings,
                MapExecutionValue(mapOf("delimiter" to TextExecutionValue(";")))))
        )

        variants.forEach { variant ->
            assertNotEquals(part.digest(), variant.digest(), "identity collision for $variant")
        }
    }

    @Test
    fun policyIdentityIsOperationalNotSemantic() {
        val part = testDataPart(
            DataRole.main,
            DataRef(null, "input.csv", mapOf(DataRef.sizeKey to "10", DataRef.modifiedKey to "20")))
        val first = CursorAdoptionIdentity(part.digest(), ReadOperationalPolicy(
            maximumExpandedBytes = 100).digest())
        val second = CursorAdoptionIdentity(part.digest(), ReadOperationalPolicy(
            maximumExpandedBytes = 200).digest())

        assertEquals(part.resolvedRead.digest(), part.resolvedRead.digest())
        assertNotEquals(first, second)
    }
}
