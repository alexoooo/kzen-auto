package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


class DataReadCoreTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun pullWrapsHasNextAndNextInOneBlockingOffload() = runBlocking {
        val control = TrackingControl()
        val cursor = TrackingCursor(listOf("first"), null) { assertTrue(control.inBlockingIo) }

        assertEquals(DataReadCore.Pull(true, "first"), DataReadCore.pull(control, cursor))
        assertEquals(DataReadCore.Pull(false, null), DataReadCore.pull(control, cursor))

        assertEquals(2, control.blockingCalls)
        assertEquals(2, cursor.hasNextCalls)
        assertEquals(1, cursor.nextCalls)
    }


    @Test
    fun pullPreservesNullableItems() = runBlocking {
        val cursor = TrackingCursor(listOf(null), null)

        val item = DataReadCore.pull(TrackingControl(), cursor)
        assertTrue(item.hasItem)
        assertNull(item.item)

        assertFalse(DataReadCore.pull(TrackingControl(), cursor).hasItem)
    }


    @Test
    fun emitNextClaimsImmediatelyBeforeSend() = runBlocking {
        val cursor = TrackingCursor(listOf("value"), DataShape.Payload(TypeMetadata.string))
        val baseline = DataReadCore.ShapeBaseline(cursor.shape, "unit 0 part 0")
        val events = mutableListOf<String>()

        val emitted = DataReadCore.emitNext(
            TrackingControl(), cursor, baseline, null,
            claimBeforeSend = { events.add("claim") },
            send = {
                events.add("send")
                assertEquals("value", it.payload)
            })

        assertTrue(emitted)
        assertEquals(listOf("claim", "send"), events)
        assertFalse(DataReadCore.emitNext(
            TrackingControl(), cursor, baseline, null,
            claimBeforeSend = { events.add("unexpected claim") },
            send = { events.add("unexpected send") }))
        assertEquals(listOf("claim", "send"), events)
    }


    @Test
    fun skipItemsUsesPullAndNamesPrematureExhaustion() = runBlocking {
        val control = TrackingControl()
        val cursor = TrackingCursor(listOf("zero", "one", "two"), null)

        DataReadCore.skipItems(control, cursor, 2)

        assertEquals(2, control.blockingCalls)
        assertEquals(DataReadCore.Pull(true, "two"), DataReadCore.pull(control, cursor))

        val exhausted = assertFailsWith<IllegalStateException> {
            DataReadCore.skipItems(control, cursor, 1)
        }
        assertTrue(exhausted.message.orEmpty().contains("item 1"))
        assertTrue(exhausted.message.orEmpty().contains("exhausted at 0"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun explicitRoleSelectsMatchingPartsInTheirOriginalOrder() {
        val unit = DataUnit.of(
            part("preview", "preview-1"),
            part("main", "main-1"),
            part("preview", "preview-2"))

        assertEquals(
            listOf("preview-1", "preview-2"),
            DataReadCore.parts(unit, "preview", 4).map { it.ref.id })

        val missing = assertFailsWith<IllegalStateException> {
            DataReadCore.parts(unit, "missing", 4)
        }
        assertTrue(missing.message.orEmpty().contains("unit 4"))
        assertTrue(missing.message.orEmpty().contains("'missing'"))
    }


    @Test
    fun blankRoleRejectsUnitsWithNoRoleOrMultipleStableRoles() {
        val empty = assertFailsWith<IllegalStateException> {
            DataReadCore.parts(DataUnit.of(), "", 2)
        }
        assertTrue(empty.message.orEmpty().contains("unit 2"))
        assertTrue(empty.message.orEmpty().contains("no readable roles"))

        val multiRole = DataUnit.of(
            part("preview", "preview-1"),
            part("main", "main-1"),
            part("preview", "preview-2"))
        val multiple = assertFailsWith<IllegalStateException> {
            DataReadCore.parts(multiRole, "", 3)
        }
        assertTrue(multiple.message.orEmpty().contains("unit 3"))
        assertTrue(multiple.message.orEmpty().contains("preview, main"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun shapeBaselineIsSharedAcrossPartsAndUnits() {
        val shape = DataShape.Tabular(HeaderListing.ofUnique(listOf("id", "value")))
        val first = DataReadCore.establishShape(
            null, DataReadCore.ShapeBaseline(shape, "unit 0 part 0"))

        val acrossPart = DataReadCore.establishShape(
            first, DataReadCore.ShapeBaseline(shape, "unit 0 part 1"))
        val acrossUnit = DataReadCore.establishShape(
            acrossPart, DataReadCore.ShapeBaseline(shape, "unit 1 part 0"))

        assertSame(first, acrossPart)
        assertSame(first, acrossUnit)
        assertEquals("unit 0 part 0", acrossUnit.origin)
    }


    @Test
    fun unknownAndKnownShapesMismatchWithBothOriginsNamed() {
        val baseline = DataReadCore.ShapeBaseline(null, "unit 0 part 0")
        val known = DataReadCore.ShapeBaseline(
            DataShape.Tabular(HeaderListing.ofUnique(listOf("value"))),
            "unit 1 part 0")

        val mismatch = assertFailsWith<IllegalStateException> {
            DataReadCore.establishShape(baseline, known)
        }
        assertTrue(mismatch.message.orEmpty().contains("unit 0 part 0 (unknown payload)"))
        assertTrue(mismatch.message.orEmpty().contains("unit 1 part 0 (columns [value])"))
    }


    @Test
    fun differentPayloadTypesMismatch() {
        val baseline = DataReadCore.ShapeBaseline(
            DataShape.Payload(TypeMetadata.string), "unit 0 part 0")
        val candidate = DataReadCore.ShapeBaseline(
            DataShape.Payload(TypeMetadata.int), "unit 0 part 1")

        val mismatch = assertFailsWith<IllegalStateException> {
            DataReadCore.establishShape(baseline, candidate)
        }
        assertTrue(mismatch.message.orEmpty().contains(TypeMetadata.string.toString()))
        assertTrue(mismatch.message.orEmpty().contains(TypeMetadata.int.toString()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun attributesArePrependedToHeaderAndRecordInDisplayOrder() {
        val cursorShape = DataShape.Tabular(HeaderListing.ofUnique(listOf("name", "amount")))
        val attributes = linkedMapOf("date" to "2026-08-23", "region" to "east")
        val effective = DataReadCore.effectiveShape(cursorShape, attributes, "unit 0 part 0")

        val message = DataReadCore.message(
            cursorShape, effective, FlatFileRecord.of("Alice", "12"), attributes)

        assertEquals(
            listOf("date", "region", "name", "amount"),
            message.flat!!.header.values.map { it.text })
        assertEquals(
            listOf("2026-08-23", "east", "Alice", "12"),
            message.flat!!.record.toList())
    }


    @Test
    fun attributeColumnCollisionNamesTheOriginAndColumn() {
        val shape = DataShape.Tabular(HeaderListing.ofUnique(listOf("date", "amount")))

        val collision = assertFailsWith<IllegalStateException> {
            DataReadCore.effectiveShape(
                shape, linkedMapOf("date" to "2026-08-23"), "unit 1 part 2")
        }
        assertTrue(collision.message.orEmpty().contains("attributes collide with columns"))
        assertTrue(collision.message.orEmpty().contains("unit 1 part 2"))
        assertTrue(collision.message.orEmpty().contains("date"))
    }


    @Test
    fun attributesRequireTabularShape() {
        val payload = DataShape.Payload(TypeMetadata.string)

        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.effectiveShape(
                payload, linkedMapOf("source" to "one"), "unit 0 part 0")
        }
        assertTrue(failure.message.orEmpty().contains("attributes=columns"))
        assertTrue(failure.message.orEmpty().contains("unit 0 part 0"))
        assertTrue(failure.message.orEmpty().contains("payload"))
    }


    @Test
    fun tabularMessageRequiresFlatFileRecord() {
        val shape = DataShape.Tabular(HeaderListing.ofUnique(listOf("value")))
        val effective = DataReadCore.ShapeBaseline(shape, "unit 5 part 6")

        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.message(shape, effective, "not a record", null)
        }
        assertTrue(failure.message.orEmpty().contains("unit 5 part 6"))
        assertTrue(failure.message.orEmpty().contains("kotlin.String"))
        assertTrue(failure.message.orEmpty().contains(FlatFileRecord::class.qualifiedName!!))
    }


    @Test
    fun supersetPlansAllAttributesBeforeOrderedDataAndProjectsMissingCells() {
        val firstShape = DataShape.Tabular(HeaderListing.ofUnique(listOf("a")))
        val secondShape = DataShape.Tabular(HeaderListing.ofUnique(listOf("b")))
        val plan = DataReadCore.planShape(listOf(
            DataReadCore.ShapeCandidate(
                firstShape, linkedMapOf("date" to "one"), "unit 0 part 0"),
            DataReadCore.ShapeCandidate(
                secondShape, linkedMapOf("group" to "two"), "unit 1 part 0")
        ), DataReadCore.schemaSuperset)

        assertEquals(
            listOf("date", "group", "a", "b"),
            assertIs<DataShape.Tabular>(plan.shape).header.values.map { it.text })
        assertEquals(
            listOf("one", DataShape.missingCellValue, "A", DataShape.missingCellValue),
            DataReadCore.message(
                firstShape, plan, FlatFileRecord.of("A"), linkedMapOf("date" to "one"))
                .flat!!.record.toList())
    }


    @Test
    fun supersetRejectsAttributeIntroducedAfterADataColumnOfTheSameName() {
        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(listOf(
                DataReadCore.ShapeCandidate(
                    DataShape.Tabular(HeaderListing.ofUnique(listOf("group"))), null, "unit 0 part 0"),
                DataReadCore.ShapeCandidate(
                    DataShape.Tabular(HeaderListing.ofUnique(listOf("value"))),
                    linkedMapOf("group" to "west"), "unit 1 part 0")
            ), DataReadCore.schemaSuperset)
        }
        assertTrue(failure.message.orEmpty().contains("attributes collide"))
        assertTrue(failure.message.orEmpty().contains("group"))
    }


    @Test
    fun strictPlanStillRejectsDifferentHeadersAndSupersetRejectsUnknownAndMixed() {
        val a = DataReadCore.ShapeCandidate(
            DataShape.Tabular(HeaderListing.ofUnique(listOf("a"))), null, "first")
        val b = DataReadCore.ShapeCandidate(
            DataShape.Tabular(HeaderListing.ofUnique(listOf("b"))), null, "second")
        val strict = assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(listOf(a, b), DataReadCore.schemaStrict)
        }
        assertTrue(strict.message.orEmpty().contains("first"))
        assertTrue(strict.message.orEmpty().contains("second"))

        assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(
                listOf(a, DataReadCore.ShapeCandidate(null, null, "unknown")),
                DataReadCore.schemaSuperset)
        }
        assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(
                listOf(a, DataReadCore.ShapeCandidate(
                    DataShape.Payload(TypeMetadata.string), null, "payload")),
                DataReadCore.schemaSuperset)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun detachedCursorCanBeAdoptedOnlyOnce() {
        val cursor = TrackingCursor(emptyList(), null)
        val detached = DataReadCore.detach(cursor)

        assertSame(cursor, DataReadCore.adopt(detached))
        val second = assertFailsWith<IllegalStateException> {
            DataReadCore.adopt(detached)
        }
        assertTrue(second.message.orEmpty().contains("already adopted or closed"))

        detached!!.close()
        assertFalse(cursor.closed)
    }


    @Test
    fun unadoptedDetachedCursorClosesAsFallback() {
        val cursor = TrackingCursor(emptyList(), null)
        val detached = DataReadCore.detach(cursor)!!

        detached.close()
        detached.close()

        assertTrue(cursor.closed)
        assertEquals(1, cursor.closeCalls)
        assertFailsWith<IllegalStateException> { DataReadCore.adopt(detached) }
    }


    @Test
    fun detachedCursorRelinquishesOwnershipEvenWhenCloseThrows() {
        var closeCalls = 0
        val throwing = object: DataCursor {
            override val shape: DataShape? = null
            override fun hasNext(): Boolean = false
            override fun next(): Any? = error("empty")
            override fun close() {
                closeCalls += 1
                error("close failed")
            }
        }
        val detached = DataReadCore.detach(throwing)!!

        assertFailsWith<IllegalStateException> { detached.close() }
        detached.close()
        assertEquals(1, closeCalls)
        assertFailsWith<IllegalStateException> { DataReadCore.adopt(detached) }
    }


    @Test
    fun cancellationDuringOffloadedCloseLeavesCursorForSynchronousFallback() = runBlocking {
        val cursor = TrackingCursor(emptyList(), null)
        val cancellingControl = object: JobControl by TrackingControl() {
            override suspend fun <R> runBlockingIo(block: () -> R): R {
                throw CancellationException("cancelled before close")
            }
        }

        assertFailsWith<CancellationException> {
            DataReadCore.close(cancellingControl, cursor)
        }
        assertFalse(cursor.closed)

        DataReadCore.closeFallback(cursor)
        assertTrue(cursor.closed)
        assertEquals(1, cursor.closeCalls)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun part(role: String, id: String): DataPart {
        return DataPart(DataRole(role), DataRef(null, id), null, null)
    }


    private class TrackingCursor(
        private val items: List<Any?>,
        override val shape: DataShape?,
        private val onAccess: () -> Unit = {}
    ): DataCursor {
        var hasNextCalls = 0
        var nextCalls = 0
        var closeCalls = 0
        var closed = false
        private var index = 0


        override fun hasNext(): Boolean {
            onAccess()
            hasNextCalls += 1
            return index < items.size
        }


        override fun next(): Any? {
            onAccess()
            nextCalls += 1
            return items[index++]
        }


        override fun close() {
            closeCalls += 1
            closed = true
        }
    }


    private open class TrackingControl: JobControl {
        var blockingCalls = 0
        var inBlockingIo = false


        override suspend fun checkpoint() {}


        override suspend fun <R> runBlockingIo(block: () -> R): R {
            blockingCalls += 1
            check(!inBlockingIo)
            inBlockingIo = true
            return try {
                block()
            }
            finally {
                inBlockingIo = false
            }
        }


        override fun scratchDir(): String {
            throw UnsupportedOperationException("DataReadCore needs no scratch directory")
        }


        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) {}


        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("DataReadCore hosts no child")
    }
}
