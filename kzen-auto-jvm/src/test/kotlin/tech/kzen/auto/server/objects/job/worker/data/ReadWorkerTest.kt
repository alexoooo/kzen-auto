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
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.OperationalDataOpener
import tech.kzen.auto.server.data.configuredTestDataPart
import tech.kzen.auto.server.data.read.OperationalDataCursor
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.testJobValue
import tech.kzen.auto.server.objects.job.worker.testProjection
import tech.kzen.auto.server.objects.job.worker.testRecord
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
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
            "payload" to { FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.anyNullable), listOf(null, "value")) }))
        val messages = mutableListOf<DataValue>()
        val control = CountingControl()
        worker(source, opener, CapturingOutput(messages)).run(control)

        assertEquals(listOf<Any?>(null, "value"), messages.map(JobDataValues::boundary))
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
        val messages = mutableListOf<DataValue>()
        worker(
            source, opener, CapturingOutput(messages),
            emit = ReadWorker.emitUnits, role = "not-present", attributes = "not-a-mode")
            .run(CountingControl())

        assertEquals(units, messages.map(JobDataValues::boundary))
        assertEquals(0, opener.openCount)
    }


    @Test
    fun freshResolutionLogsOneDigestedTeasedManifestEvent() = runBlocking {
        val units = (0 until 12).map {
            DataUnit.of(configuredTestDataPart(
                DataRole.main,
                DataRef(
                    null, "unit-$it",
                    linkedMapOf(DataRef.sizeKey to "$it", DataRef.modifiedKey to "2026-08-24T12:00:00Z")),
                null))
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
        val header = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value")))
        val source = FakeSource(manifest(DataUnit.of(part("a"), part("b"))))
        val opener = FakeOpener(mapOf(
            "a" to { FakeCursor(header, first) },
            "b" to { FakeCursor(header, second) }))
        val messages = mutableListOf<DataValue>()
        val control = CountingControl()
        worker(source, opener, CapturingOutput(messages)).run(control)

        assertEquals(1000, messages.size)
        assertEquals("a0", testRecord(messages.first()).getString(0))
        assertEquals("b399", testRecord(messages.last()).getString(0))
        assertEquals(1004, control.blockingCount)
    }


    @Test
    fun attributesPrependColumnsAndCollisionsCloseCursor() = runBlocking {
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value")))
        val attributes = linkedMapOf("date" to "2026-08-23", "group" to "A")
        val source = FakeSource(manifest(DataUnit(attributes, listOf(part("ok")))))
        val okCursor = FakeCursor(shape, listOf(FlatFileRecord.of("7")))
        val messages = mutableListOf<DataValue>()
        worker(
            source, FakeOpener(mapOf("ok" to { okCursor })), CapturingOutput(messages),
            attributes = ReadWorker.attributesColumns)
            .run(CountingControl())

        assertEquals(
            listOf("date", "group", "value"),
            testProjection(messages.single()).header.values.map { it.text })
        assertEquals(listOf("2026-08-23", "A", "7"), testRecord(messages.single()).toList())

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
        val shapeA = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val shapeB = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
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
            "main" to { FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("main-value")) },
            "reference" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("reference-value"))
            }))
        val messages = mutableListOf<DataValue>()
        worker(source, opener, CapturingOutput(messages), role = "reference")
            .run(CountingControl())
        assertEquals(listOf("reference-value"), messages.map(JobDataValues::boundary))

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
            LegacyDataShapeBridge.payload(TypeMetadata.string),
            listOf("a", "b", "c", "d"))
        val opener = FakeOpener(mapOf("a" to { cursor }))
        val firstMessages = mutableListOf<DataValue>()
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val firstControl = ParkingControl(parked, release)
        val first = worker(source, opener, CapturingOutput(firstMessages, batchSize = 2))
        val job = launch { first.run(firstControl) }
        parked.await()

        val captured = first.captureMigrationState()
        job.cancelAndJoin()
        source.result = manifest(unit("a"), unit("new-file"))

        val resumedMessages = mutableListOf<DataValue>()
        val resumedSource = FakeSource(source.result)
        val resumedOpener = FakeOpener(mapOf(
            "a" to { error("adopted cursor must not reopen") },
            "new-file" to { error("captured manifest must not include new files") }))
        val resumedControl = CountingControl()
        val resumed = worker(resumedSource, resumedOpener, CapturingOutput(resumedMessages, batchSize = 2))
        resumed.loadMigrationState(captured)
        resumed.run(resumedControl)

        assertEquals(listOf("a", "b", "c", "d"),
            (firstMessages + resumedMessages).map(JobDataValues::boundary))
        assertEquals(0, resumedSource.resolveCount)
        assertEquals(0, resumedOpener.openCount)
        assertTrue(resumedControl.logs.isEmpty(), "a carried manifest must not be logged again")
        assertTrue(resumedControl.blockingCount > 0, "the adopted cursor is driven by the resumed control")
        assertTrue(cursor.closed)
    }


    @Test
    fun migrationPreservesEstablishedShapeBaselineAcrossParts() = runBlocking {
        val shapeA = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val shapeB = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
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
        val oldCursor = FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("old-1", "old-2"))
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
        val messages = mutableListOf<DataValue>()
        val resumed = worker(
            newSource,
            FakeOpener(mapOf("new" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("new-value"))
            })),
            CapturingOutput(messages), sourceKey = "new")
        resumed.loadMigrationState(captured)
        assertTrue(oldCursor.closed, "an incompatible detached cursor is closed before restart")
        resumed.run(CountingControl())

        assertEquals(listOf("new-value"), messages.map(JobDataValues::boundary))
        assertEquals(1, newSource.resolveCount)
    }


    @Test
    fun changedResolvedSourceLocationRestartsAndClosesDetachedCursor() = runBlocking {
        val source = FakeSource(manifest(unit("old")))
        val oldCursor = FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("one", "two"))
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
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("fresh"))
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
                LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("old-1", "old-2"))
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
                    FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), emptyList())
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

        val resumedMessages = mutableListOf<DataValue>()
        val resumedSource = FakeSource(DataManifest(units))
        val resumed = worker(
            resumedSource, FakeOpener(emptyMap()), CapturingOutput(resumedMessages),
            emit = ReadWorker.emitUnits)
        resumed.loadMigrationState(first.captureMigrationState())
        resumed.run(CountingControl())

        assertEquals(listOf(units[1]), resumedMessages.map(JobDataValues::boundary))
        assertEquals(0, resumedSource.resolveCount)
    }


    @Test
    fun itemPositionIsClaimedBeforeSendAndReopenedCursorSkipsIt() = runBlocking {
        val source = FakeSource(manifest(unit("items")))
        val firstCursor = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("first", "second"))
        val first = worker(
            source, FakeOpener(mapOf("items" to { firstCursor })), ThrowingOutput())
        assertFailsWith<IllegalStateException> {
            first.run(CountingControl())
        }
        assertTrue(firstCursor.closed)

        val resumedMessages = mutableListOf<DataValue>()
        val resumedSource = FakeSource(source.result)
        val resumed = worker(
            resumedSource,
            FakeOpener(mapOf("items" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("first", "second"))
            })),
            CapturingOutput(resumedMessages))
        resumed.loadMigrationState(first.captureMigrationState())
        resumed.run(CountingControl())

        assertEquals(listOf("second"), resumedMessages.map(JobDataValues::boundary))
        assertEquals(0, resumedSource.resolveCount)
    }


    @Test
    fun payloadFlowUsesOnlyStaticShapeAndReportsSourceFailures() {
        val source = FakeSource(
            manifest(unit("unused")),
            LegacyDataShapeBridge.payload(TypeMetadata.string))
        val worker = worker(source, FakeOpener(emptyMap()), CapturingOutput())
        val attempt = worker.payloadFlow(JobLaneDescriptor.unknown, laneContext())
        assertEquals(TypeMetadata.string, attempt.lane.payloadType)
        assertEquals(0, source.resolveCount)

        val columns = worker(
            source, FakeOpener(emptyMap()), CapturingOutput(),
            attributes = ReadWorker.attributesColumns)
            .payloadFlow(JobLaneDescriptor.unknown, laneContext())
        assertContains(columns.errorMessage!!, "attributes=columns")

        val failed = worker(source, FakeOpener(emptyMap()), CapturingOutput())
        failed.loadSourceResolution(WorkerDefinitionResolution.Failed("dangling source"))
        val failureAttempt = failed.payloadFlow(JobLaneDescriptor.unknown, laneContext())
        assertNull(failureAttempt.lane.payloadType)
        assertContains(failureAttempt.errorMessage!!, "dangling")

        val units = worker(
            source, FakeOpener(emptyMap()), CapturingOutput(), emit = ReadWorker.emitUnits)
            .payloadFlow(JobLaneDescriptor.unknown, laneContext())
        assertEquals(DataUnit::class.qualifiedName, units.lane.payloadType!!.className.asString())
    }


    @Test
    fun supersetPreinspectsGlobalManifestAndEmitsAttrsFirstProjectedRecords() = runBlocking {
        val aShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val bShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
        val source = FakeSource(manifest(
            DataUnit(linkedMapOf("date" to "one"), listOf(part("a"))),
            DataUnit(linkedMapOf("group" to "two"), listOf(part("b")))))
        val opener = FakeOpener(
            mapOf(
                "a" to { FakeCursor(aShape, listOf(FlatFileRecord.of("A"))) },
                "b" to { FakeCursor(bShape, listOf(FlatFileRecord.of("B"))) }),
            mapOf("a" to aShape, "b" to bShape))
        val messages = mutableListOf<DataValue>()

        worker(
            source, opener, CapturingOutput(messages),
            attributes = ReadWorker.attributesColumns,
            schemaMode = DataReadCore.schemaSuperset).run(CountingControl())

        assertEquals(2, opener.inspectCount)
        assertEquals(listOf("date", "group", "a", "b"),
            testProjection(messages.first()).header.values.map { it.text })
        assertEquals(
            listOf("one", LegacyDataShapeBridge.missingCellValue, "A", LegacyDataShapeBridge.missingCellValue),
            testRecord(messages[0]).toList())
        assertEquals(
            listOf(LegacyDataShapeBridge.missingCellValue, "two", LegacyDataShapeBridge.missingCellValue, "B"),
            testRecord(messages[1]).toList())
    }


    @Test
    fun hundredPartSupersetUsesOneBoundedInspectionPerPart() = runBlocking {
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value")))
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
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
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
            LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("changed"))), emptyList())
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
        return configuredTestDataPart(DataRole(role), DataRef(null, id), null)
    }


    private fun manifest(vararg units: DataUnit): DataManifest = DataManifest(units.toList())


    private fun laneContext(): JobLaneContext {
        return JobLaneContext(
            BindingSchema.empty, GraphStructure.empty, ReadWorker::class.java.classLoader)
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
    ): OperationalDataOpener {
        var openCount = 0
        var inspectCount = 0


        override suspend fun inspectShape(context: DataContext, part: DataPart): DataShape? {
            inspectCount += 1
            return inspected[part.ref.id]
        }


        override suspend fun open(context: DataContext, part: DataPart): DataCursor {
            openCount += 1
            val cursor = factories[part.ref.id]?.invoke()
                ?: error("No cursor for ${part.ref.id}")
            val identity = adoptionIdentity(part)
            return object: OperationalDataCursor, DataCursor by cursor {
                override val adoptionIdentity = identity
            }
        }


        override fun adoptionIdentity(part: DataPart): CursorAdoptionIdentity =
            CursorAdoptionIdentity(part.digest(), Digest.ofUtf8("fake-read-policy"))
    }


    private class FakeCursor(
        shape: DataShape?,
        items: List<Any?>
    ): DataCursor {
        override val shape: DataShape = shape ?: LegacyDataShapeBridge.runtimeUnknown()
        private val registry = DefaultDataAdapterRegistry()
        private val iterator = items.map { item ->
            if (item is DataValue) {
                item
            }
            else if (item is FlatFileRecord) {
                item.attachHeader(FlatRecordHeader(this.shape.itemType))
                DataValue(item, DataNode(0))
            }
            else {
                registry.lift(item)
            }
        }.iterator()
        var closed = false


        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): DataValue = iterator.next()
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
        private val sink: MutableList<DataValue> = mutableListOf(),
        private val batchSize: Int = 1024
    ): ChannelOutput<Any?> {
        override suspend fun send(element: Any?) {
            sink.add(testJobValue(element))
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
