package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.job.worker.JobMessage
import tech.kzen.auto.server.objects.job.worker.WorkerLane
import tech.kzen.auto.server.objects.job.worker.WorkerLaneContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.system.measureTimeMillis


class ReadWorkerTest {
    private val workerLocation = ObjectLocation(
        DocumentPath.parse("test/read-worker-test.yaml"),
        ObjectPath.parse("main.workers/read"))
    private val sourceLocation = ObjectLocation(
        workerLocation.documentPath,
        ObjectPath.parse("main.sources/input"))


    @Test
    fun emitsNullablePayloadsWithOneBlockingPullPerItem() = runBlocking {
        val source = FakeSource(manifest(unit("payload")))
        val opener = FakeOpener(mapOf(
            "payload" to { FakeCursor(DataShape.Payload(TypeMetadata.anyNullable), listOf(null, "value")) }))
        val messages = mutableListOf<JobMessage>()
        val control = CountingControl()
        worker(source, opener, CapturingOutput(messages)).run(control)

        assertEquals(listOf<Any?>(null, "value"), messages.map { it.payload })
        assertEquals(1, source.resolveCount)
        assertEquals(4, control.blockingCount, "two items, the exhausted pull, and close")
        assertEquals(2L, control.progressValues.last()["emitted"])
        assertEquals(1L, control.progressValues.last()["units"])
    }


    @Test
    fun nullSourceFailsAtRuntimeWithClearMessage() = runBlocking {
        val read = ReadWorker(
            CapturingOutput(), null, ReadWorker.emitItems, "", ReadWorker.attributesIgnore,
            workerLocation, DataOpenerLookup(FakeOpener(emptyMap())))
        read.loadSourceResolution(WorkerDefinitionResolution.Failed("No data source selected"))

        val failure = assertFailsWith<IllegalStateException> {
            read.run(CountingControl())
        }
        assertContains(failure.message!!, "No data source selected")
    }


    @Test
    fun emitsWholeUnitsWithoutOpeningPartsAndIgnoresItemKnobs() = runBlocking {
        val units = listOf(unit("a"), unit("b"), unit("c"))
        val source = FakeSource(DataManifest(units))
        val opener = FakeOpener(emptyMap())
        val messages = mutableListOf<JobMessage>()
        worker(
            source, opener, CapturingOutput(messages),
            emit = ReadWorker.emitUnits, role = "not-present", attributes = "not-a-mode")
            .run(CountingControl())

        assertEquals(units, messages.map { it.payload })
        assertEquals(0, opener.openCount)
    }


    @Test
    fun freshResolutionLogsOneDigestedTeasedManifestEvent() = runBlocking {
        val units = (0 until 12).map {
            DataUnit.of(DataPart(
                DataRole.main,
                DataRef(
                    null, "unit-$it",
                    linkedMapOf(DataRef.sizeKey to "$it", DataRef.modifiedKey to "2026-08-24T12:00:00Z")),
                null, null))
        }
        val source = FakeSource(DataManifest(units))
        val control = CountingControl()
        worker(
            source, FakeOpener(emptyMap()), CapturingOutput(), emit = ReadWorker.emitUnits)
            .run(control)

        val event = control.logs.single()
        assertEquals(setOf("digest", "totalCount", "teasedManifest", "truncated"), event.keys)
        assertEquals(source.result.digest().toString(), event["digest"])
        assertEquals(12L, event["totalCount"])
        assertEquals(true, event["truncated"])
        val teaser = DataManifest.ofExecutionValue(event["teasedManifest"] as ExecutionValue)
        assertEquals(10, teaser.units.size)
        assertEquals(units.take(10), teaser.units)
        assertTrue(teaser.units.all { it.parts.single().ref.fingerprintOrNull() != null })
    }


    @Test
    fun readsOneThousandRowsAndMultiplePartsInManifestOrder() = runBlocking {
        val first = (0 until 600).map { FlatFileRecord.of("a$it") }
        val second = (0 until 400).map { FlatFileRecord.of("b$it") }
        val header = DataShape.Tabular(HeaderListing.ofUnique(listOf("value")))
        val source = FakeSource(manifest(DataUnit.of(part("a"), part("b"))))
        val opener = FakeOpener(mapOf(
            "a" to { FakeCursor(header, first) },
            "b" to { FakeCursor(header, second) }))
        val messages = mutableListOf<JobMessage>()
        val control = CountingControl()
        worker(source, opener, CapturingOutput(messages)).run(control)

        assertEquals(1000, messages.size)
        assertEquals("a0", messages.first().flat!!.record.getString(0))
        assertEquals("b399", messages.last().flat!!.record.getString(0))
        assertEquals(1004, control.blockingCount)
    }


    @Test
    fun attributesPrependColumnsAndCollisionsCloseCursor() = runBlocking {
        val shape = DataShape.Tabular(HeaderListing.ofUnique(listOf("value")))
        val attributes = linkedMapOf("date" to "2026-08-23", "group" to "A")
        val source = FakeSource(manifest(DataUnit(attributes, listOf(part("ok")))))
        val okCursor = FakeCursor(shape, listOf(FlatFileRecord.of("7")))
        val messages = mutableListOf<JobMessage>()
        worker(
            source, FakeOpener(mapOf("ok" to { okCursor })), CapturingOutput(messages),
            attributes = ReadWorker.attributesColumns)
            .run(CountingControl())

        assertEquals(
            listOf("date", "group", "value"),
            messages.single().flat!!.header.values.map { it.text })
        assertEquals(listOf("2026-08-23", "A", "7"), messages.single().flat!!.record.toList())

        val collisionSource = FakeSource(manifest(DataUnit(mapOf("value" to "A"), listOf(part("bad")))))
        val collisionCursor = FakeCursor(shape, listOf(FlatFileRecord.of("7")))
        val failure = assertFailsWith<IllegalStateException> {
            worker(
                collisionSource, FakeOpener(mapOf("bad" to { collisionCursor })), CapturingOutput(),
                attributes = ReadWorker.attributesColumns)
                .run(CountingControl())
        }
        assertContains(failure.message!!, "value")
        assertTrue(collisionCursor.closed)
    }


    @Test
    fun shapeMismatchNamesBothPartsAndClosesSecondCursor() = runBlocking {
        val shapeA = DataShape.Tabular(HeaderListing.ofUnique(listOf("a")))
        val shapeB = DataShape.Tabular(HeaderListing.ofUnique(listOf("b")))
        val source = FakeSource(manifest(DataUnit.of(part("first"), part("second"))))
        val secondCursor = FakeCursor(shapeB, listOf(FlatFileRecord.of("2")))
        val failure = assertFailsWith<IllegalStateException> {
            worker(
                source,
                FakeOpener(mapOf(
                    "first" to { FakeCursor(shapeA, listOf(FlatFileRecord.of("1"))) },
                    "second" to { secondCursor })),
                CapturingOutput())
                .run(CountingControl())
        }

        assertContains(failure.message!!, "first")
        assertContains(failure.message!!, "second")
        assertTrue(secondCursor.closed)
    }


    @Test
    fun roleSelectionIsAppliedAtWorkerBoundary() = runBlocking {
        val twoRoleUnit = DataUnit.of(
            part("main", "main"), part("reference", "reference"))
        val source = FakeSource(manifest(twoRoleUnit))
        val opener = FakeOpener(mapOf(
            "main" to { FakeCursor(DataShape.Payload(TypeMetadata.string), listOf("main-value")) },
            "reference" to {
                FakeCursor(DataShape.Payload(TypeMetadata.string), listOf("reference-value"))
            }))
        val messages = mutableListOf<JobMessage>()
        worker(source, opener, CapturingOutput(messages), role = "reference")
            .run(CountingControl())
        assertEquals(listOf("reference-value"), messages.map { it.payload })

        val ambiguous = assertFailsWith<IllegalStateException> {
            worker(source, opener, CapturingOutput()).run(CountingControl())
        }
        assertContains(ambiguous.message!!, "main")
        assertContains(ambiguous.message!!, "reference")

        val missing = assertFailsWith<IllegalStateException> {
            worker(source, opener, CapturingOutput(), role = "missing").run(CountingControl())
        }
        assertContains(missing.message!!, "unit 0")
        assertContains(missing.message!!, "missing")
    }


    @Test
    fun migrationAdoptsCursorUsesNewControlAndKeepsCapturedManifest() = runBlocking {
        val originalManifest = manifest(unit("a"))
        val source = FakeSource(originalManifest)
        val cursor = FakeCursor(
            DataShape.Payload(TypeMetadata.string),
            listOf("a", "b", "c", "d"))
        val opener = FakeOpener(mapOf("a" to { cursor }))
        val firstMessages = mutableListOf<JobMessage>()
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val firstControl = ParkingControl(parked, release)
        val first = worker(source, opener, CapturingOutput(firstMessages, batchSize = 2))
        val job = launch { first.run(firstControl) }
        parked.await()

        val captured = first.captureMigrationState()
        job.cancelAndJoin()
        source.result = manifest(unit("a"), unit("new-file"))

        val resumedMessages = mutableListOf<JobMessage>()
        val resumedSource = FakeSource(source.result)
        val resumedOpener = FakeOpener(mapOf(
            "a" to { error("adopted cursor must not reopen") },
            "new-file" to { error("captured manifest must not include new files") }))
        val resumedControl = CountingControl()
        val resumed = worker(resumedSource, resumedOpener, CapturingOutput(resumedMessages, batchSize = 2))
        resumed.loadMigrationState(captured)
        resumed.run(resumedControl)

        assertEquals(listOf("a", "b", "c", "d"),
            (firstMessages + resumedMessages).map { it.payload })
        assertEquals(0, resumedSource.resolveCount)
        assertEquals(0, resumedOpener.openCount)
        assertTrue(resumedControl.logs.isEmpty(), "a carried manifest must not be logged again")
        assertTrue(resumedControl.blockingCount > 0, "the adopted cursor is driven by the resumed control")
        assertTrue(cursor.closed)
    }


    @Test
    fun migrationPreservesEstablishedShapeBaselineAcrossParts() = runBlocking {
        val shapeA = DataShape.Tabular(HeaderListing.ofUnique(listOf("a")))
        val shapeB = DataShape.Tabular(HeaderListing.ofUnique(listOf("b")))
        val source = FakeSource(manifest(DataUnit.of(part("first"), part("second"))))
        val firstCursor = FakeCursor(shapeA, listOf(FlatFileRecord.of("1")))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            source,
            FakeOpener(mapOf(
                "first" to { firstCursor },
                "second" to { error("second part must open only after migration") })),
            CapturingOutput(batchSize = 1))
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val secondCursor = FakeCursor(shapeB, listOf(FlatFileRecord.of("2")))
        val resumed = worker(
            source,
            FakeOpener(mapOf(
                "first" to { error("detached first cursor must be adopted") },
                "second" to { secondCursor })),
            CapturingOutput(batchSize = 1))
        resumed.loadMigrationState(captured)

        val failure = assertFailsWith<IllegalStateException> {
            resumed.run(CountingControl())
        }
        assertContains(failure.message!!, "first")
        assertContains(failure.message!!, "second")
        assertTrue(secondCursor.closed)
    }


    @Test
    fun changedSourceIdentityRestartsAndClosesDetachedCursor() = runBlocking {
        val oldSource = FakeSource(manifest(unit("old")))
        val oldCursor = FakeCursor(DataShape.Payload(TypeMetadata.string), listOf("old-1", "old-2"))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            oldSource, FakeOpener(mapOf("old" to { oldCursor })),
            CapturingOutput(batchSize = 1), sourceKey = "old")
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val newSource = FakeSource(manifest(unit("new")))
        val messages = mutableListOf<JobMessage>()
        val resumed = worker(
            newSource,
            FakeOpener(mapOf("new" to {
                FakeCursor(DataShape.Payload(TypeMetadata.string), listOf("new-value"))
            })),
            CapturingOutput(messages), sourceKey = "new")
        resumed.loadMigrationState(captured)
        assertTrue(oldCursor.closed, "an incompatible detached cursor is closed before restart")
        resumed.run(CountingControl())

        assertEquals(listOf("new-value"), messages.map { it.payload })
        assertEquals(1, newSource.resolveCount)
    }


    @Test
    fun changedResolvedSourceLocationRestartsAndClosesDetachedCursor() = runBlocking {
        val source = FakeSource(manifest(unit("old")))
        val oldCursor = FakeCursor(DataShape.Payload(TypeMetadata.string), listOf("one", "two"))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            source, FakeOpener(mapOf("old" to { oldCursor })),
            CapturingOutput(batchSize = 1))
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val changedLocation = ObjectLocation(
            sourceLocation.documentPath, ObjectPath.parse("main.sources/other"))
        val changed = worker(
            source,
            FakeOpener(mapOf("old" to {
                FakeCursor(DataShape.Payload(TypeMetadata.string), listOf("fresh"))
            })),
            CapturingOutput(), resolvedLocation = changedLocation)
        changed.loadMigrationState(captured)

        assertTrue(oldCursor.closed)
    }


    @Test
    fun changedEmitRoleOrAttributesRejectsDetachedCursor() = runBlocking {
        val changes = listOf(
            Triple(ReadWorker.emitUnits, "", ReadWorker.attributesIgnore),
            Triple(ReadWorker.emitItems, "main", ReadWorker.attributesIgnore),
            Triple(ReadWorker.emitItems, "", ReadWorker.attributesColumns))

        for ((changedEmit, changedRole, changedAttributes) in changes) {
            val oldSource = FakeSource(manifest(unit("old")))
            val oldCursor = FakeCursor(
                DataShape.Payload(TypeMetadata.string), listOf("old-1", "old-2"))
            val parked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = worker(
                oldSource, FakeOpener(mapOf("old" to { oldCursor })),
                CapturingOutput(batchSize = 1))
            val job = launch { first.run(ParkingControl(parked, release)) }
            parked.await()
            val captured = first.captureMigrationState()
            job.cancelAndJoin()

            val changed = worker(
                oldSource, FakeOpener(emptyMap()), CapturingOutput(),
                emit = changedEmit, role = changedRole, attributes = changedAttributes)
            changed.loadMigrationState(captured)
            assertTrue(oldCursor.closed)
        }
    }


    @Test
    fun finishedAndEmptyStatesDoNotResolveAgain() = runBlocking {
        for (initial in listOf(manifest(unit("one")), DataManifest(emptyList()))) {
            val firstSource = FakeSource(initial)
            val first = worker(
                firstSource,
                FakeOpener(mapOf("one" to {
                    FakeCursor(DataShape.Payload(TypeMetadata.string), emptyList())
                })),
                CapturingOutput())
            first.run(CountingControl())

            val resumedSource = FakeSource(initial)
            val resumed = worker(resumedSource, FakeOpener(emptyMap()), CapturingOutput())
            resumed.loadMigrationState(first.captureMigrationState())
            resumed.run(CountingControl())
            assertEquals(0, resumedSource.resolveCount)
        }
    }


    @Test
    fun unitPositionIsClaimedBeforeSend() = runBlocking {
        val units = listOf(unit("first"), unit("second"))
        val first = worker(
            FakeSource(DataManifest(units)), FakeOpener(emptyMap()),
            ThrowingOutput(), emit = ReadWorker.emitUnits)
        assertFailsWith<IllegalStateException> {
            first.run(CountingControl())
        }

        val resumedMessages = mutableListOf<JobMessage>()
        val resumedSource = FakeSource(DataManifest(units))
        val resumed = worker(
            resumedSource, FakeOpener(emptyMap()), CapturingOutput(resumedMessages),
            emit = ReadWorker.emitUnits)
        resumed.loadMigrationState(first.captureMigrationState())
        resumed.run(CountingControl())

        assertEquals(listOf(units[1]), resumedMessages.map { it.payload })
        assertEquals(0, resumedSource.resolveCount)
    }


    @Test
    fun itemPositionIsClaimedBeforeSendAndReopenedCursorSkipsIt() = runBlocking {
        val source = FakeSource(manifest(unit("items")))
        val firstCursor = FakeCursor(
            DataShape.Payload(TypeMetadata.string), listOf("first", "second"))
        val first = worker(
            source, FakeOpener(mapOf("items" to { firstCursor })), ThrowingOutput())
        assertFailsWith<IllegalStateException> {
            first.run(CountingControl())
        }
        assertTrue(firstCursor.closed)

        val resumedMessages = mutableListOf<JobMessage>()
        val resumedSource = FakeSource(source.result)
        val resumed = worker(
            resumedSource,
            FakeOpener(mapOf("items" to {
                FakeCursor(DataShape.Payload(TypeMetadata.string), listOf("first", "second"))
            })),
            CapturingOutput(resumedMessages))
        resumed.loadMigrationState(first.captureMigrationState())
        resumed.run(CountingControl())

        assertEquals(listOf("second"), resumedMessages.map { it.payload })
        assertEquals(0, resumedSource.resolveCount)
    }


    @Test
    fun payloadFlowUsesOnlyStaticShapeAndReportsSourceFailures() {
        val source = FakeSource(
            manifest(unit("unused")),
            DataShape.Payload(TypeMetadata.string))
        val worker = worker(source, FakeOpener(emptyMap()), CapturingOutput())
        val attempt = worker.payloadFlow(WorkerLane.unknown, laneContext())
        assertEquals(TypeMetadata.string, attempt.lane.payloadType)
        assertEquals(0, source.resolveCount)

        val columns = worker(
            source, FakeOpener(emptyMap()), CapturingOutput(),
            attributes = ReadWorker.attributesColumns)
            .payloadFlow(WorkerLane.unknown, laneContext())
        assertContains(columns.errorMessage!!, "attributes=columns")

        val failed = worker(source, FakeOpener(emptyMap()), CapturingOutput())
        failed.loadSourceResolution(WorkerDefinitionResolution.Failed("dangling source"))
        val failureAttempt = failed.payloadFlow(WorkerLane.unknown, laneContext())
        assertNull(failureAttempt.lane.payloadType)
        assertContains(failureAttempt.errorMessage!!, "dangling")

        val units = worker(
            source, FakeOpener(emptyMap()), CapturingOutput(), emit = ReadWorker.emitUnits)
            .payloadFlow(WorkerLane.unknown, laneContext())
        assertEquals(DataUnit::class.qualifiedName, units.lane.payloadType!!.className.asString())
    }


    @Test
    fun supersetPreinspectsGlobalManifestAndEmitsAttrsFirstProjectedRecords() = runBlocking {
        val aShape = DataShape.Tabular(HeaderListing.ofUnique(listOf("a")))
        val bShape = DataShape.Tabular(HeaderListing.ofUnique(listOf("b")))
        val source = FakeSource(manifest(
            DataUnit(linkedMapOf("date" to "one"), listOf(part("a"))),
            DataUnit(linkedMapOf("group" to "two"), listOf(part("b")))))
        val opener = FakeOpener(
            mapOf(
                "a" to { FakeCursor(aShape, listOf(FlatFileRecord.of("A"))) },
                "b" to { FakeCursor(bShape, listOf(FlatFileRecord.of("B"))) }),
            mapOf("a" to aShape, "b" to bShape))
        val messages = mutableListOf<JobMessage>()

        worker(
            source, opener, CapturingOutput(messages),
            attributes = ReadWorker.attributesColumns,
            schemaMode = DataReadCore.schemaSuperset).run(CountingControl())

        assertEquals(2, opener.inspectCount)
        assertEquals(listOf("date", "group", "a", "b"),
            messages.first().flat!!.header.values.map { it.text })
        assertEquals(
            listOf("one", DataShape.missingCellValue, "A", DataShape.missingCellValue),
            messages[0].flat!!.record.toList())
        assertEquals(
            listOf(DataShape.missingCellValue, "two", DataShape.missingCellValue, "B"),
            messages[1].flat!!.record.toList())
    }


    @Test
    fun hundredPartSupersetUsesOneBoundedInspectionPerPart() = runBlocking {
        val shape = DataShape.Tabular(HeaderListing.ofUnique(listOf("value")))
        val ids = (0 until 100).map { "part-$it" }
        val source = FakeSource(DataManifest(ids.map(::unit)))
        val opener = FakeOpener(
            ids.associateWith { id -> { FakeCursor(shape, listOf(FlatFileRecord.of(id))) } },
            ids.associateWith { shape })

        val elapsed = measureTimeMillis {
            worker(
                source, opener, CapturingOutput(),
                schemaMode = DataReadCore.schemaSuperset).run(CountingControl())
        }

        assertEquals(100, opener.inspectCount)
        assertEquals(100, opener.openCount)
        assertTrue(elapsed < 5_000, "100 in-memory bounded inspections took ${elapsed}ms")
    }


    @Test
    fun supersetUnknownFailsBeforeOpenAndInspectedOpenRaceClosesCursor() = runBlocking {
        val shape = DataShape.Tabular(HeaderListing.ofUnique(listOf("a")))
        val unknownOpener = FakeOpener(
            mapOf("a" to { FakeCursor(shape, emptyList()) }),
            mapOf("a" to null))
        assertFailsWith<IllegalStateException> {
            worker(
                FakeSource(manifest(unit("a"))), unknownOpener, CapturingOutput(),
                schemaMode = DataReadCore.schemaSuperset).run(CountingControl())
        }
        assertEquals(0, unknownOpener.openCount)

        val opened = FakeCursor(
            DataShape.Tabular(HeaderListing.ofUnique(listOf("changed"))), emptyList())
        val raced = FakeOpener(mapOf("a" to { opened }), mapOf("a" to shape))
        assertFailsWith<IllegalStateException> {
            worker(
                FakeSource(manifest(unit("a"))), raced, CapturingOutput(),
                schemaMode = DataReadCore.schemaSuperset).run(CountingControl())
        }
        assertTrue(opened.closed)
    }


    private fun worker(
        source: DataSource,
        opener: DataOpener,
        output: ChannelOutput<Any?>,
        emit: String = ReadWorker.emitItems,
        role: String = "",
        attributes: String = ReadWorker.attributesIgnore,
        sourceKey: String = "source",
        resolvedLocation: ObjectLocation = sourceLocation,
        schemaMode: String = DataReadCore.schemaStrict
    ): ReadWorker {
        val worker = ReadWorker(
            output, ObjectReference.parse("input"), emit, role, attributes,
            workerLocation, DataOpenerLookup(opener), schemaMode)
        worker.loadSourceResolution(
            WorkerDefinitionResolution.Resolved(
                resolvedLocation, Digest.ofUtf8(sourceKey), source))
        return worker
    }


    private fun unit(id: String): DataUnit = DataUnit.of(part(id))


    private fun part(id: String, role: String = DataRole.main.name): DataPart {
        return DataPart(DataRole(role), DataRef(null, id), null, null)
    }


    private fun manifest(vararg units: DataUnit): DataManifest = DataManifest(units.toList())


    private fun laneContext(): WorkerLaneContext {
        return WorkerLaneContext(
            TupleDefinition.empty, GraphStructure.empty, ReadWorker::class.java.classLoader)
    }


    private class FakeSource(
        var result: DataManifest,
        private val shape: DataShape? = null
    ): DataSource {
        var resolveCount = 0


        override suspend fun resolve(context: DataContext): DataResolveResult {
            resolveCount += 1
            return DataResolveResult(result, emptyList())
        }


        override fun staticShape(role: DataRole?): DataShape? = shape
    }


    private class FakeOpener(
        private val factories: Map<String, () -> DataCursor>,
        private val inspected: Map<String, DataShape?> = emptyMap()
    ): DataOpener {
        var openCount = 0
        var inspectCount = 0


        override suspend fun inspectShape(context: DataContext, part: DataPart): DataShape? {
            inspectCount += 1
            return inspected[part.ref.id]
        }


        override suspend fun open(context: DataContext, part: DataPart): DataCursor {
            openCount += 1
            return factories[part.ref.id]?.invoke()
                ?: error("No cursor for ${part.ref.id}")
        }
    }


    private class FakeCursor(
        override val shape: DataShape?,
        items: List<Any?>
    ): DataCursor {
        private val iterator = items.iterator()
        var closed = false


        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): Any? = iterator.next()
        override fun close() {
            closed = true
        }
    }


    private open class CountingControl: JobControl {
        var blockingCount = 0
        val progressValues = mutableListOf<Map<String, Any?>>()
        val logs = mutableListOf<Map<String, Any?>>()


        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R {
            blockingCount += 1
            return block()
        }
        override fun scratchDir(): String = error("ReadWorker needs no scratch directory")
        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) {
            progressValues.add(value)
        }
        override fun log(location: ObjectLocation, value: Map<String, Any?>) {
            logs.add(value)
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            error("ReadWorker hosts no child")
    }


    private class ParkingControl(
        private val parked: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>
    ): CountingControl() {
        private var checkpoints = 0


        override suspend fun checkpoint() {
            checkpoints += 1
            if (checkpoints == 2) {
                parked.complete(Unit)
                release.await()
            }
        }
    }


    private class CapturingOutput(
        private val sink: MutableList<JobMessage> = mutableListOf(),
        private val batchSize: Int = 1024
    ): ChannelOutput<Any?> {
        override suspend fun send(element: Any?) {
            sink.add(assertIs<JobMessage>(element))
        }
        override suspend fun flush() {}
        override fun batchSize(): Int = batchSize
        override fun close() {}
    }


    private class ThrowingOutput: ChannelOutput<Any?> {
        override suspend fun send(element: Any?) {
            throw IllegalStateException("send failed")
        }
        override suspend fun flush() {}
        override fun batchSize(): Int = 1024
        override fun close() {}
    }
}
