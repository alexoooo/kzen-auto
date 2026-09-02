package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.recordOf
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.data.read.OperationalDataCursor
import tech.kzen.auto.server.data.configuredTestDataPart
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.util.digest.Digest
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

        val first = DataReadCore.pull(control, cursor)
        assertTrue(first.hasItem)
        assertEquals("first", first.item!!.access.readText(first.item.root))
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
        assertEquals(DataState.Null, item.item!!.access.state(item.item.root))

        assertFalse(DataReadCore.pull(TrackingControl(), cursor).hasItem)
    }


    @Test
    fun emitNextClaimsImmediatelyBeforeSend() = runBlocking {
        val cursor = TrackingCursor(listOf("value"), LegacyDataShapeBridge.payload(TypeMetadata.string))
        val baseline = DataReadCore.ShapeBaseline(cursor.shape, "unit 0 part 0")
        val events = mutableListOf<String>()

        val emitted = DataReadCore.emitNext(
            TrackingControl(), cursor, baseline, null,
            claimBeforeSend = { events.add("claim") },
            send = {
                events.add("send")
                assertEquals("value", JobDataValues.boundary(it))
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
        val third = DataReadCore.pull(control, cursor)
        assertEquals("two", third.item!!.access.readText(third.item.root))

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
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("id", "value")))
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
        val baseline = DataReadCore.ShapeBaseline(
            LegacyDataShapeBridge.runtimeUnknown(), "unit 0 part 0")
        val known = DataReadCore.ShapeBaseline(
            LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value"))),
            "unit 1 part 0")

        val mismatch = assertFailsWith<IllegalStateException> {
            DataReadCore.establishShape(baseline, known)
        }
        assertTrue(mismatch.message.orEmpty().contains("unit 0 part 0"))
        assertTrue(mismatch.message.orEmpty().contains("unit 1 part 0"))
    }


    @Test
    fun differentPayloadTypesMismatch() {
        val baseline = DataReadCore.ShapeBaseline(
            LegacyDataShapeBridge.payload(TypeMetadata.string), "unit 0 part 0")
        val candidate = DataReadCore.ShapeBaseline(
            LegacyDataShapeBridge.payload(TypeMetadata.int), "unit 0 part 1")

        val mismatch = assertFailsWith<IllegalStateException> {
            DataReadCore.establishShape(baseline, candidate)
        }
        assertTrue(mismatch.message.orEmpty().contains("Text"))
        assertTrue(mismatch.message.orEmpty().contains("Integer"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun attributesArePrependedToHeaderAndRecordInDisplayOrder() {
        val cursorShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("name", "amount")))
        val attributes = linkedMapOf("date" to "2026-08-23", "region" to "east")
        val effective = DataReadCore.effectiveShape(
            cursorShape, attributes, "unit 0 part 0")

        val message = DataReadCore.message(
            cursorShape, effective, flatValue(cursorShape, "Alice", "12"), attributes)

        assertEquals(
            listOf("date", "region", "name", "amount"),
            JobDataValues.projection(message).header.values.map { it.text })
        assertEquals(
            listOf("2026-08-23", "east", "Alice", "12"),
            JobDataValues.record(JobDataValues.projection(message)).toList())
    }


    @Test
    fun attributeColumnCollisionNamesTheOriginAndColumn() {
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("date", "amount")))

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
        val payload = LegacyDataShapeBridge.payload(TypeMetadata.string)

        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.effectiveShape(
                payload, linkedMapOf("source" to "one"), "unit 0 part 0")
        }
        assertTrue(failure.message.orEmpty().contains("attributes=columns"))
        assertTrue(failure.message.orEmpty().contains("unit 0 part 0"))
        assertTrue(failure.message.orEmpty().contains("value"))
    }


    @Test
    fun tabularMessageProjectsAnyValueAccessWithoutFlatteningItsScalarContract() {
        val amount = DataField(FieldId("amount"), DataType.Scalar(ScalarKind.Integer(64)))
        val shape = typedShape(amount)
        val effective = DataReadCore.planShape(listOf(
            DataReadCore.ShapeCandidate(shape, null, "unit 5 part 6"),
            DataReadCore.ShapeCandidate(
                typedShape(DataField(FieldId("note"), DataType.Scalar(ScalarKind.Text))),
                null,
                "unit 7 part 8")
        ), DataReadCore.schemaSuperset)
        val literal = LiteralDataValues.lift(recordOf("amount" to 42L), shape.itemType)

        val projected = DataReadCore.message(shape, effective, literal, null)
        val amountNode = projected.access.field(projected.root, amount.id)
        val noteNode = projected.access.field(projected.root, FieldId("note"))

        assertEquals(42L, projected.access.readLong(amountNode))
        assertEquals(DataState.Absent, projected.access.state(noteNode))
        assertEquals(amount.type, projected.access.contract(amountNode).structural)
    }


    @Test
    fun supersetPlansAllAttributesBeforeOrderedDataAndProjectsMissingCells() {
        val firstShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val secondShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
        val plan = DataReadCore.planShape(listOf(
            DataReadCore.ShapeCandidate(
                firstShape, linkedMapOf("date" to "one"), "unit 0 part 0"),
            DataReadCore.ShapeCandidate(
                secondShape, linkedMapOf("group" to "two"), "unit 1 part 0")
        ), DataReadCore.schemaSuperset)

        assertEquals(
            listOf("date", "group", "a", "b"),
            LegacyDataShapeBridge.headerOrNull(plan.shape)!!.values.map { it.text })
        assertEquals(
            listOf("one", LegacyDataShapeBridge.missingCellValue, "A", LegacyDataShapeBridge.missingCellValue),
            DataReadCore.message(
                firstShape, plan, flatValue(firstShape, "A"), linkedMapOf("date" to "one"))
                .let(JobDataValues::projection)
                .let(JobDataValues::record)
                .toList())
    }


    @Test
    fun supersetPreservesTypedNullableFieldsAndOnlyAddsOptionalPresence() {
        val amount = DataField(
            FieldId("amount"),
            DataType.Scalar(ScalarKind.Decimal, nullable = true))
        val plan = DataReadCore.planShape(listOf(
            DataReadCore.ShapeCandidate(typedShape(amount), null, "first"),
            DataReadCore.ShapeCandidate(
                typedShape(DataField(FieldId("note"), DataType.Scalar(ScalarKind.Text))),
                null,
                "second")
        ), DataReadCore.schemaSuperset)

        val fields = (plan.shape.itemType.structural as DataType.Record).fields
        assertEquals(amount.type, fields.single { it.id == amount.id }.type)
        assertTrue(fields.single { it.id == amount.id }.optional)
        assertTrue(fields.single { it.id == FieldId("note") }.optional)
    }


    @Test
    fun supersetPreservesFieldNativeMetadata() {
        val amount = DataField(FieldId("amount"), DataType.Scalar(ScalarKind.Integer(64)))
        val fieldPath = DataTypePath(listOf(DataPathSegment.Field(amount.id)))
        val sourceContract = DataContract(
            DataType.Record(listOf(amount)),
            mapOf(fieldPath to TypeMetadata.long))
        val plan = DataReadCore.planShape(listOf(
            DataReadCore.ShapeCandidate(typedShape(sourceContract), null, "typed"),
            DataReadCore.ShapeCandidate(
                typedShape(DataField(FieldId("note"), DataType.Scalar(ScalarKind.Text))),
                null,
                "other")
        ), DataReadCore.schemaSuperset)

        assertEquals(TypeMetadata.long, plan.shape.itemType.nativeByPath[fieldPath])
    }


    @Test
    fun supersetRejectsSameFieldWithDifferentNullabilityAndNamesBothContracts() {
        val nullable = typedShape(DataField(
            FieldId("amount"), DataType.Scalar(ScalarKind.Decimal, nullable = true)))
        val required = typedShape(DataField(
            FieldId("amount"), DataType.Scalar(ScalarKind.Decimal, nullable = false)))

        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(listOf(
                DataReadCore.ShapeCandidate(nullable, null, "nullable source"),
                DataReadCore.ShapeCandidate(required, null, "required source")
            ), DataReadCore.schemaSuperset)
        }

        assertTrue(failure.message.orEmpty().contains("nullable source"))
        assertTrue(failure.message.orEmpty().contains("required source"))
        assertTrue(failure.message.orEmpty().contains("Decimal"))
    }


    @Test
    fun attributeColumnsPrependTextWithoutReducingTypedDataFields() {
        val amount = DataField(FieldId("amount"), DataType.Scalar(ScalarKind.Decimal))
        val effective = DataReadCore.effectiveShape(
            typedShape(amount), linkedMapOf("group" to "west"), "typed source")
        val fields = (effective.shape.itemType.structural as DataType.Record).fields

        assertEquals(listOf(FieldId("group"), amount.id), fields.map { it.id })
        assertEquals(DataType.Scalar(ScalarKind.Text), fields.first().type)
        assertEquals(amount.type, fields.last().type)
    }


    @Test
    fun supersetRejectsAttributeIntroducedAfterADataColumnOfTheSameName() {
        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(listOf(
                DataReadCore.ShapeCandidate(
                    LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("group"))), null, "unit 0 part 0"),
                DataReadCore.ShapeCandidate(
                    LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value"))),
                    linkedMapOf("group" to "west"), "unit 1 part 0")
            ), DataReadCore.schemaSuperset)
        }
        assertTrue(failure.message.orEmpty().contains("attributes collide"))
        assertTrue(failure.message.orEmpty().contains("group"))
    }


    @Test
    fun strictPlanStillRejectsDifferentHeadersAndSupersetRejectsUnknownAndMixed() {
        val a = DataReadCore.ShapeCandidate(
            LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a"))), null, "first")
        val b = DataReadCore.ShapeCandidate(
            LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b"))), null, "second")
        val strict = assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(listOf(a, b), DataReadCore.schemaStrict)
        }
        assertTrue(strict.message.orEmpty().contains("first"))
        assertTrue(strict.message.orEmpty().contains("second"))

        assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(
                listOf(a, DataReadCore.ShapeCandidate(
                    LegacyDataShapeBridge.runtimeUnknown(), null, "unknown")),
                DataReadCore.schemaSuperset)
        }
        assertFailsWith<IllegalStateException> {
            DataReadCore.planShape(
                listOf(a, DataReadCore.ShapeCandidate(
                    LegacyDataShapeBridge.payload(TypeMetadata.string), null, "payload")),
                DataReadCore.schemaSuperset)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun detachedCursorCanBeAdoptedOnlyOnce() {
        val tracking = TrackingCursor(emptyList(), null)
        val identity = CursorAdoptionIdentity(Digest.ofUtf8("part"), Digest.ofUtf8("policy"))
        val cursor = object: OperationalDataCursor, DataCursor by tracking {
            override val adoptionIdentity = identity
        }
        val detached = DataReadCore.detach(cursor)

        assertSame(cursor, DataReadCore.adopt(detached, identity))
        val second = assertFailsWith<IllegalStateException> {
            DataReadCore.adopt(detached, identity)
        }
        assertTrue(second.message.orEmpty().contains("already adopted or closed"))

        detached!!.close()
        assertFalse(tracking.closed)
    }


    @Test
    fun detachedCursorWithoutAdoptionIdentityFailsClosed() {
        val cursor = TrackingCursor(emptyList(), null)
        val detached = DataReadCore.detach(cursor)

        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.adopt(detached)
        }

        assertTrue(failure.message.orEmpty().contains("incompatible"))
        assertTrue(cursor.closed)
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
            override val shape: DataShape = LegacyDataShapeBridge.runtimeUnknown()
            override fun hasNext(): Boolean = false
            override fun next(): DataValue = error("empty")
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
    fun detachedConfiguredCursorRejectsOneChangedRunLimitAndCloses() {
        var closed = false
        val partIdentity = Digest.ofUtf8("part")
        val capturedPolicy = ReadOperationalPolicy(maximumFields = 10)
        val currentPolicy = ReadOperationalPolicy(maximumFields = 11)
        val cursor = object: OperationalDataCursor {
            override val adoptionIdentity = CursorAdoptionIdentity(
                partIdentity, capturedPolicy.digest())
            override val shape = LegacyDataShapeBridge.runtimeUnknown()
            override fun hasNext() = false
            override fun next(): DataValue = error("empty")
            override fun close() { closed = true }
        }

        val detached = DataReadCore.detach(cursor)
        val failure = assertFailsWith<IllegalStateException> {
            DataReadCore.adopt(detached, CursorAdoptionIdentity(partIdentity, currentPolicy.digest()))
        }

        assertTrue(closed)
        assertTrue(failure.message.orEmpty().contains("incompatible"))
        assertTrue(failure.message.orEmpty().contains("current="))
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
        return configuredTestDataPart(DataRole(role), DataRef(null, id), null)
    }


    private class TrackingCursor(
        private val items: List<Any?>,
        shape: DataShape?,
        private val onAccess: () -> Unit = {}
    ): DataCursor {
        override val shape: DataShape = shape ?: LegacyDataShapeBridge.runtimeUnknown()
        private val registry = DefaultDataAdapterRegistry()
        private val values = items.map { item -> registry.lift(item, this.shape.itemType) }
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


        override fun next(): DataValue {
            onAccess()
            nextCalls += 1
            return values[index++]
        }


        override fun close() {
            closeCalls += 1
            closed = true
        }
    }


    private fun flatValue(shape: DataShape, vararg fields: String): DataValue {
        val record = FlatFileRecord.of(*fields)
        record.attachHeader(FlatRecordHeader(shape.itemType))
        return DataValue(record, DataNode(0))
    }


    private fun typedShape(vararg fields: DataField): DataShape {
        return typedShape(DataContract(DataType.Record(fields.toList())))
    }


    private fun typedShape(contract: DataContract): DataShape {
        val base = LegacyDataShapeBridge.tabular(
            HeaderListing.ofUnique((contract.structural as DataType.Record).fields.map { it.id.name }))
        return DataShape(
            contract,
            base.provenance,
            base.stability,
            base.diagnostics)
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
