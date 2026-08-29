package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.auto.server.objects.job.worker.testJobValue
import tech.kzen.auto.server.objects.job.worker.testProjection
import tech.kzen.auto.server.objects.job.worker.testRecord
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.lib.common.exec.data.binding.BindingSchema
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
import tech.kzen.lib.platform.ClassName
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


class ReadPartWorkerTest {
    private val workerLocation = ObjectLocation(
        DocumentPath.parse("test/read-part-worker-test.yaml"),
        ObjectPath.parse("main.workers/readPart"))


    @Test
    fun readsRolesPartsNullablePayloadsAndOffloadsEveryCursorOperation() = runBlocking {
        val unit = DataUnit.of(
            part("main", "main"),
            part("reference-a", "reference"),
            part("reference-b", "reference"))
        val opener = FakeOpener(mapOf(
            "main" to { FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("ignored")) },
            "reference-a" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.anyNullable), listOf(null, "a"))
            },
            "reference-b" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.anyNullable), listOf("b"))
            }))
        val messages = mutableListOf<DataValue>()
        val control = CountingControl()
        worker(BatchInput(listOf(unit)), opener, CapturingOutput(messages), role = "reference")
            .run(control)

        assertEquals(listOf<Any?>(null, "a", "b"), messages.map(JobDataValues::boundary))
        assertEquals(listOf("reference-a", "reference-b"), opener.opened)
        assertEquals(7, control.blockingCount, "three items, two exhausted pulls, and two closes")
        assertEquals(1L, control.progressValues.last()["units"])
        assertEquals(3L, control.progressValues.last()["emitted"])
    }


    @Test
    fun blankAndMissingRolesFailDescriptivelyAndCloseNothingUnopened() = runBlocking {
        val unit = DataUnit.of(part("main", "main"), part("reference", "reference"))
        val opener = FakeOpener(emptyMap())

        val ambiguous = assertFailsWith<IllegalStateException> {
            worker(BatchInput(listOf(unit)), opener, CapturingOutput()).run(CountingControl())
        }
        assertContains(ambiguous.message!!, "main")
        assertContains(ambiguous.message!!, "reference")

        val missing = assertFailsWith<IllegalStateException> {
            worker(BatchInput(listOf(unit)), opener, CapturingOutput(), role = "missing")
                .run(CountingControl())
        }
        assertContains(missing.message!!, "unit 0")
        assertContains(missing.message!!, "missing")
        assertEquals(0, opener.opened.size)
    }


    @Test
    fun attributesPrependInOrderAndCollisionClosesCursor() = runBlocking {
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value")))
        val attributes = linkedMapOf("date" to "2026-08-24", "group" to "A")
        val unit = DataUnit(attributes, listOf(part("ok")))
        val messages = mutableListOf<DataValue>()
        worker(
            BatchInput(listOf(unit)),
            FakeOpener(mapOf("ok" to { FakeCursor(shape, listOf(FlatFileRecord.of("7"))) })),
            CapturingOutput(messages),
            attributes = ReadPartWorker.attributesColumns)
            .run(CountingControl())

        assertEquals(listOf("date", "group", "value"),
            testProjection(messages.single()).header.values.map { it.text })
        assertEquals(listOf("2026-08-24", "A", "7"), testRecord(messages.single()).toList())

        val collisionCursor = FakeCursor(shape, listOf(FlatFileRecord.of("8")))
        val collision = DataUnit(mapOf("value" to "collision"), listOf(part("bad")))
        val failure = assertFailsWith<IllegalStateException> {
            worker(
                BatchInput(listOf(collision)),
                FakeOpener(mapOf("bad" to { collisionCursor })),
                CapturingOutput(), attributes = ReadPartWorker.attributesColumns)
                .run(CountingControl())
        }
        assertContains(failure.message!!, "value")
        assertTrue(collisionCursor.closed)
    }


    @Test
    fun rejectsNonDataUnitPayloadAndShapeMismatchClosesCursor() = runBlocking {
        val payloadFailure = assertFailsWith<IllegalStateException> {
            worker(
                BatchInput.messages(listOf(JobDataValues.lift(42))),
                FakeOpener(emptyMap()), CapturingOutput())
                .run(CountingControl())
        }
        assertContains(payloadFailure.message!!, "DataUnit")
        assertContains(payloadFailure.message!!, "kotlin.Int")

        val firstShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val secondShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
        val second = FakeCursor(secondShape, listOf(FlatFileRecord.of("2")))
        val mismatch = assertFailsWith<IllegalStateException> {
            worker(
                BatchInput(listOf(DataUnit.of(part("first"), part("second")))),
                FakeOpener(mapOf(
                    "first" to { FakeCursor(firstShape, listOf(FlatFileRecord.of("1"))) },
                    "second" to { second })),
                CapturingOutput())
                .run(CountingControl())
        }
        assertContains(mismatch.message!!, "first")
        assertContains(mismatch.message!!, "second")
        assertTrue(second.closed)
    }


    @Test
    fun cadenceCheckpointsThroughoutOneThousandItemExpansion() = runBlocking {
        val items = (0 until 1000).map { "row-$it" }
        val cursor = FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), items)
        val control = CountingControl()
        val messages = mutableListOf<DataValue>()
        worker(
            BatchInput(listOf(DataUnit.of(part("large")))),
            FakeOpener(mapOf("large" to { cursor })),
            CapturingOutput(messages, batchSize = 10))
            .run(control)

        assertEquals(items, messages.map(JobDataValues::boundary))
        assertTrue(control.checkpointCount >= 101, "leading checkpoint plus 100 output cadence checkpoints")
        assertEquals(1002, control.blockingCount)
        assertTrue(cursor.closed)
    }


    @Test
    fun cancellationAtCadenceCheckpointClosesActiveCursor() = runBlocking {
        val cursor = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string), (0 until 100).map { it })
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val output = CapturingOutput(batchSize = 2)
        val running = worker(
            BatchInput(listOf(DataUnit.of(part("items")))),
            FakeOpener(mapOf("items" to { cursor })),
            output)
        val job = launch { running.run(ParkingControl(parked, release)) }
        parked.await()
        job.cancelAndJoin()

        assertTrue(cursor.closed)
        assertEquals(1, output.closeCount)
    }


    @Test
    fun expandingOutputClosesExactlyOnceOnNormalAndSetupFailure() = runBlocking {
        val normalOutput = CapturingOutput()
        worker(BatchInput(emptyList()), FakeOpener(emptyMap()), normalOutput)
            .run(CountingControl())
        assertEquals(1, normalOutput.closeCount)

        val failedOutput = CapturingOutput()
        val failed = worker(
            BatchInput(emptyList()), FakeOpener(emptyMap()), failedOutput,
            attributes = "invalid")
        assertFailsWith<IllegalArgumentException> {
            failed.run(CountingControl())
        }
        assertEquals(1, failedOutput.closeCount)
    }


    @Test
    fun unadoptedCompositeMigrationStateOwnsAndClosesDetachedCursor() = runBlocking {
        val cursor = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string), (0 until 10).map { it })
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val running = worker(
            BatchInput(listOf(DataUnit.of(part("items")))),
            FakeOpener(mapOf("items" to { cursor })),
            CapturingOutput(batchSize = 2))
        val job = launch { running.run(ParkingControl(parked, release)) }
        parked.await()

        val captured = assertIs<AutoCloseable>(running.captureMigrationState())
        job.cancelAndJoin()
        assertTrue(!cursor.closed, "capture detached the cursor from the outgoing worker")
        captured.close()

        assertTrue(cursor.closed, "the base composite state closes its unadopted subclass cursor")
    }


    @Test
    fun readWorkerSnapshotIsClosedWhenWorkerIsReplacedByReadPart() = runBlocking {
        val cursor = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string), (0 until 10).map { it })
        val source = object: DataSource {
            override suspend fun resolve(context: DataContext): DataResolveResult =
                DataResolveResult(DataManifest(listOf(DataUnit.of(part("items")))), emptyList())


            override fun staticShape(role: DataRole?): DataShape? = null
        }
        val read = ReadWorker(
            CapturingOutput(batchSize = 2), ObjectReference.parse("input"),
            ReadWorker.emitItems, "", ReadWorker.attributesIgnore,
            workerLocation, DataOpenerLookup(FakeOpener(mapOf("items" to { cursor }))),
            DataReadCore.schemaStrict)
        read.loadSourceResolution(
            WorkerDefinitionResolution.Resolved(workerLocation, Digest.ofUtf8("source"), source))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch { read.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = read.captureMigrationState()
        job.cancelAndJoin()
        assertTrue(!cursor.closed)

        worker(BatchInput(emptyList()), FakeOpener(emptyMap()), CapturingOutput())
            .loadMigrationState(captured)

        assertTrue(cursor.closed)
    }


    @Test
    fun readPartSnapshotIsClosedWhenWorkerIsReplacedByReadWorker() = runBlocking {
        val cursor = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string), (0 until 10).map { it })
        val readPart = worker(
            BatchInput(listOf(DataUnit.of(part("items")))),
            FakeOpener(mapOf("items" to { cursor })),
            CapturingOutput(batchSize = 2))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch { readPart.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = readPart.captureMigrationState()
        job.cancelAndJoin()
        assertTrue(!cursor.closed)

        val read = ReadWorker(
            CapturingOutput(), ObjectReference.parse("input"),
            ReadWorker.emitItems, "", ReadWorker.attributesIgnore,
            workerLocation, DataOpenerLookup(FakeOpener(emptyMap())))
        read.loadMigrationState(captured)

        assertTrue(cursor.closed)
    }


    @Test
    fun sameConfigMigrationCarriesActiveBatchAndAdoptsCursorExactlyOnce() = runBlocking {
        val units = listOf(
            DataUnit.of(part("unit-1")),
            DataUnit.of(part("unit-2")),
            DataUnit.of(part("unit-3")))
        val firstCursor = FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("u1"))
        val secondCursor = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("u2-0", "u2-1", "u2-2", "u2-3", "u2-4"))
        val input = BatchInput(units)
        val firstMessages = mutableListOf<DataValue>()
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            input,
            FakeOpener(mapOf("unit-1" to { firstCursor }, "unit-2" to { secondCursor })),
            CapturingOutput(firstMessages, batchSize = 4))
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()

        assertTrue(input.isDrained(), "the complete physical batch has already left the input")
        val captured = first.captureMigrationState()
        job.cancelAndJoin()
        assertTrue(firstCursor.closed)
        assertTrue(!secondCursor.closed, "the active cursor ownership was detached")

        val resumedMessages = mutableListOf<DataValue>()
        val resumedOpener = FakeOpener(mapOf(
            "unit-2" to { error("active cursor must be adopted") },
            "unit-3" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("u3"))
            }))
        val resumed = worker(
            BatchInput(emptyList()), resumedOpener,
            CapturingOutput(resumedMessages, batchSize = 4))
        resumed.loadMigrationState(captured)
        val resumedControl = CountingControl()
        resumed.run(resumedControl)

        assertEquals(
            listOf("u1", "u2-0", "u2-1", "u2-2", "u2-3", "u2-4", "u3"),
            (firstMessages + resumedMessages).map(JobDataValues::boundary))
        assertEquals(listOf("unit-3"), resumedOpener.opened)
        assertTrue(secondCursor.closed)
        assertTrue(resumedControl.blockingCount > 0)
    }


    @Test
    fun realChannelsSeparateBaseOwnedInputBatchFromParkedOutputAndResumeExactlyOnce() = runBlocking {
        val units = listOf(
            DataUnit.of(part("unit-1")),
            DataUnit.of(part("unit-2")),
            DataUnit.of(part("unit-3")))
        val inputChannel = JobChannel(capacity = 1, batchSize = 3)
        val inputProducer = inputChannel.newProducer()
        for (unit in units) {
            inputProducer.send(JobDataValues.lift(unit))
        }
        inputProducer.flush()
        inputProducer.close()

        val activeSecond = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string),
            listOf("u2-0", "u2-1", "u2-2", "u2-3", "u2-4"))
        val outputChannel = JobChannel(capacity = 0, batchSize = 3)
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            inputChannel.input,
            FakeOpener(mapOf(
                "unit-1" to {
                    FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("u1"))
                },
                "unit-2" to { activeSecond })),
            outputChannel.newProducer())
        val job = launch { first.run(ParkingControl(parked, release)) }

        withTimeout(5_000) {
            while (outputChannel.blockedCount() == 0) {
                yield()
            }
        }
        assertEquals(emptyList(), inputChannel.drainBuffered(),
            "all three units are owned by ExpandingTransformWorker's active batch")
        val carriedOutput = outputChannel.drainBuffered()
        assertEquals(listOf("u1", "u2-0", "u2-1"), payloads(carriedOutput))
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val resumedInput = JobChannel(capacity = 1, batchSize = 3)
        resumedInput.newProducer().close()
        val resumedOutput = JobChannel(capacity = 8, batchSize = 3)
        resumedOutput.preload(carriedOutput)
        val resumedOpener = FakeOpener(mapOf(
            "unit-2" to { error("the active cursor must be adopted") },
            "unit-3" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("u3"))
            }))
        val resumed = worker(
            resumedInput.input, resumedOpener, resumedOutput.newProducer())
        resumed.loadMigrationState(captured)
        resumed.run(CountingControl())

        assertEquals(
            listOf("u1", "u2-0", "u2-1", "u2-2", "u2-3", "u2-4", "u3"),
            payloads(resumedOutput.drainBuffered()))
        assertEquals(listOf("unit-3"), resumedOpener.opened)
        assertTrue(activeSecond.closed)
    }


    @Test
    fun migrationAfterExactFinalItemObservesEofThenAdvancesCarriedUnit() = runBlocking {
        val units = listOf(DataUnit.of(part("first")), DataUnit.of(part("second")))
        val firstCursor = FakeCursor(
            LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("a", "b", "c"))
        val firstMessages = mutableListOf<DataValue>()
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            BatchInput(units), FakeOpener(mapOf("first" to { firstCursor })),
            CapturingOutput(firstMessages, batchSize = 3))
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val resumedMessages = mutableListOf<DataValue>()
        val resumedOpener = FakeOpener(mapOf(
            "first" to { error("the exhausted cursor must be adopted to observe EOF") },
            "second" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("next"))
            }))
        val resumedControl = CountingControl()
        val resumed = worker(
            BatchInput(emptyList()), resumedOpener, CapturingOutput(resumedMessages, batchSize = 3))
        resumed.loadMigrationState(captured)
        resumed.run(resumedControl)

        assertEquals(listOf("a", "b", "c", "next"),
            (firstMessages + resumedMessages).map(JobDataValues::boundary))
        assertEquals(listOf("second"), resumedOpener.opened)
        assertTrue(firstCursor.closed)
        assertTrue(resumedControl.blockingCount >= 4,
            "resumed control observes first-part EOF, closes it, pulls next item, then observes next EOF")
    }


    @Test
    fun shapeBaselineRejectsMismatchAcrossUnitsAndClosesSecondCursor() = runBlocking {
        val shapeA = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val shapeB = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
        val secondCursor = FakeCursor(shapeB, listOf(FlatFileRecord.of("2")))
        val failure = assertFailsWith<IllegalStateException> {
            worker(
                BatchInput(listOf(DataUnit.of(part("first")), DataUnit.of(part("second")))),
                FakeOpener(mapOf(
                    "first" to { FakeCursor(shapeA, listOf(FlatFileRecord.of("1"))) },
                    "second" to { secondCursor })),
                CapturingOutput())
                .run(CountingControl())
        }

        assertContains(failure.message!!, "unit 0")
        assertContains(failure.message!!, "unit 1")
        assertTrue(secondCursor.closed)
    }


    @Test
    fun sameConfigMigrationPreservesWorkerWideShapeBaseline() = runBlocking {
        val shapeA = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val shapeB = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
        val firstCursor = FakeCursor(shapeA, listOf(FlatFileRecord.of("1")))
        val units = listOf(DataUnit.of(part("first")), DataUnit.of(part("second")))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            BatchInput(units), FakeOpener(mapOf("first" to { firstCursor })),
            CapturingOutput(batchSize = 1))
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val secondCursor = FakeCursor(shapeB, listOf(FlatFileRecord.of("2")))
        val resumed = worker(
            BatchInput(emptyList()),
            FakeOpener(mapOf(
                "first" to { error("the first cursor must be adopted") },
                "second" to { secondCursor })),
            CapturingOutput(batchSize = 1))
        resumed.loadMigrationState(captured)
        val failure = assertFailsWith<IllegalStateException> {
            resumed.run(CountingControl())
        }

        assertContains(failure.message!!, "unit 0")
        assertContains(failure.message!!, "unit 1")
        assertTrue(firstCursor.closed)
        assertTrue(secondCursor.closed)
    }


    @Test
    fun changedRoleReopensAndSkipsGlobalOrdinalAcrossParts() = runBlocking {
        val unit = DataUnit.of(
            part("main-a", "main"), part("main-b", "main"),
            part("ref-a", "reference"), part("ref-b", "reference"))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val firstMessages = mutableListOf<DataValue>()
        val activeMain = FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("m2", "m3"))
        val first = worker(
            BatchInput(listOf(unit)),
            FakeOpener(mapOf(
                "main-a" to {
                    FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("m0", "m1"))
                },
                "main-b" to { activeMain })),
            CapturingOutput(firstMessages, batchSize = 3), role = "main")
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val resumedMessages = mutableListOf<DataValue>()
        val resumedOpener = FakeOpener(mapOf(
            "ref-a" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("r0", "r1"))
            },
            "ref-b" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("r2", "r3"))
            }))
        val resumed = worker(
            BatchInput(emptyList()), resumedOpener,
            CapturingOutput(resumedMessages, batchSize = 3), role = "reference")
        resumed.loadMigrationState(captured)
        resumed.run(CountingControl())

        assertEquals(listOf("m0", "m1", "m2", "r3"),
            (firstMessages + resumedMessages).map(JobDataValues::boundary))
        assertEquals(listOf("ref-a", "ref-b"), resumedOpener.opened)
        assertTrue(activeMain.closed, "changed config closes rather than adopts the detached cursor")
    }


    @Test
    fun changedAttributesWithEmptyAttributesReopensAndSkipsTheEmittedPrefix() = runBlocking {
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value")))
        val unit = DataUnit.of(part("items"))
        val oldCursor = FakeCursor(
            shape, listOf(FlatFileRecord.of("1"), FlatFileRecord.of("2")))
        val firstMessages = mutableListOf<DataValue>()
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            BatchInput(listOf(unit)), FakeOpener(mapOf("items" to { oldCursor })),
            CapturingOutput(firstMessages, batchSize = 1))
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val reopened = FakeCursor(
            shape, listOf(FlatFileRecord.of("1"), FlatFileRecord.of("2")))
        val resumedMessages = mutableListOf<DataValue>()
        val resumed = worker(
            BatchInput(emptyList()), FakeOpener(mapOf("items" to { reopened })),
            CapturingOutput(resumedMessages), attributes = ReadPartWorker.attributesColumns)
        resumed.loadMigrationState(captured)
        resumed.run(CountingControl())

        assertTrue(oldCursor.closed)
        assertTrue(reopened.closed)
        assertEquals(listOf("1", "2"),
            (firstMessages + resumedMessages).map { testRecord(it).getString(0) })
    }


    @Test
    fun changedAttributesWithNonemptyAttributesFailsOnEffectiveShapeBeforeNextSend() = runBlocking {
        val unit = DataUnit(mapOf("group" to "A"), listOf(part("items")))
        val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("value")))
        val oldCursor = FakeCursor(
            shape,
            listOf(FlatFileRecord.of("1"), FlatFileRecord.of("2")))
        val parked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = worker(
            BatchInput(listOf(unit)), FakeOpener(mapOf("items" to { oldCursor })),
            CapturingOutput(batchSize = 1))
        val job = launch { first.run(ParkingControl(parked, release)) }
        parked.await()
        val captured = first.captureMigrationState()
        job.cancelAndJoin()

        val reopened = FakeCursor(
            shape, listOf(FlatFileRecord.of("1"), FlatFileRecord.of("2")))
        val resumedMessages = mutableListOf<DataValue>()
        val resumed = worker(
            BatchInput(emptyList()), FakeOpener(mapOf("items" to { reopened })),
            CapturingOutput(resumedMessages),
            attributes = ReadPartWorker.attributesColumns)
        resumed.loadMigrationState(captured)
        assertTrue(oldCursor.closed)
        val failure = assertFailsWith<IllegalStateException> {
            resumed.run(CountingControl())
        }
        assertContains(failure.message!!, "Data shape mismatch")
        assertEquals(emptyList(), resumedMessages)
        assertTrue(reopened.closed)
    }


    @Test
    fun itemPositionIsClaimedBeforeSendAndReopenedCursorSkipsIt() = runBlocking {
        val unit = DataUnit.of(part("items"))
        val firstCursor = FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("first", "second"))
        val first = worker(
            BatchInput(listOf(unit)), FakeOpener(mapOf("items" to { firstCursor })), ThrowingOutput())
        assertFailsWith<IllegalStateException> { first.run(CountingControl()) }
        assertTrue(firstCursor.closed)

        val resumedMessages = mutableListOf<DataValue>()
        val resumed = worker(
            BatchInput(emptyList()),
            FakeOpener(mapOf("items" to {
                FakeCursor(LegacyDataShapeBridge.payload(TypeMetadata.string), listOf("first", "second"))
            })),
            CapturingOutput(resumedMessages))
        resumed.loadMigrationState(first.captureMigrationState())
        resumed.run(CountingControl())
        assertEquals(listOf("second"), resumedMessages.map(JobDataValues::boundary))
    }


    @Test
    fun payloadFlowAcceptsOnlyUnknownOrNonNullableDataUnitAndNeverInfersOutput() {
        val worker = worker(BatchInput(emptyList()), FakeOpener(emptyMap()), CapturingOutput())
        val unknown = worker.payloadFlow(JobLaneDescriptor.unknown, laneContext())
        assertNull(unknown.errorMessage)
        assertNull(unknown.lane.payloadType)

        val dataUnit = TypeMetadata(dataUnitClassName(), emptyList(), false)
        val valid = worker.payloadFlow(JobLaneDescriptor(dataUnit, HeaderListing.empty), laneContext())
        assertNull(valid.errorMessage)
        assertNull(valid.lane.payloadType)
        assertNull(valid.lane.flatColumns)

        val nullable = worker.payloadFlow(
            JobLaneDescriptor(
                TypeMetadata(dataUnit.className, dataUnit.generics, true),
                HeaderListing.empty),
            laneContext())
        assertContains(nullable.errorMessage!!, "DataUnit?")

        val wrong = worker.payloadFlow(
            JobLaneDescriptor(TypeMetadata.int, HeaderListing.empty), laneContext())
        assertContains(wrong.errorMessage!!, "Int")

        val flat = worker.payloadFlow(
            JobLaneDescriptor(null, HeaderListing.ofUnique(listOf("value"))), laneContext())
        assertContains(flat.errorMessage!!, "flat columns")
    }


    @Test
    fun supersetPlansCurrentUnitAcrossAllPartsAndProjectsMissingCells() = runBlocking {
        val aShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val bShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
        val unit = DataUnit.of(part("a"), part("b"))
        val opener = FakeOpener(
            mapOf(
                "a" to { FakeCursor(aShape, listOf(FlatFileRecord.of("A"))) },
                "b" to { FakeCursor(bShape, listOf(FlatFileRecord.of("B"))) }),
            mapOf("a" to aShape, "b" to bShape))
        val messages = mutableListOf<DataValue>()

        worker(
            BatchInput(listOf(unit)), opener, CapturingOutput(messages),
            schemaMode = DataReadCore.schemaSuperset).run(CountingControl())

        assertEquals(2, opener.inspectCount)
        assertEquals(listOf("a", "b"), testProjection(messages.first()).header.values.map { it.text })
        assertEquals(listOf("A", LegacyDataShapeBridge.missingCellValue), testRecord(messages[0]).toList())
        assertEquals(listOf(LegacyDataShapeBridge.missingCellValue, "B"), testRecord(messages[1]).toList())
    }


    @Test
    fun supersetRetainsWorkerWideBaselineAndRejectsDifferentLaterUnitPlan() = runBlocking {
        val aShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a")))
        val bShape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("b")))
        val aCursor = FakeCursor(aShape, listOf(FlatFileRecord.of("A")))
        val opener = FakeOpener(
            mapOf(
                "a" to { aCursor },
                "b" to { FakeCursor(bShape, listOf(FlatFileRecord.of("B"))) }),
            mapOf("a" to aShape, "b" to bShape))
        val messages = mutableListOf<DataValue>()

        val failure = assertFailsWith<IllegalStateException> {
            worker(
                BatchInput(listOf(DataUnit.of(part("a")), DataUnit.of(part("b")))),
                opener, CapturingOutput(messages),
                schemaMode = DataReadCore.schemaSuperset).run(CountingControl())
        }

        assertTrue(failure.message.orEmpty().contains("Data shape mismatch"))
        assertEquals(1, messages.size)
        assertEquals(listOf("a"), opener.opened)
        assertTrue(aCursor.closed)
    }


    private fun worker(
        input: ChannelInput<*>,
        opener: DataOpener,
        output: ChannelOutput<DataValue>,
        role: String = "",
        attributes: String = ReadPartWorker.attributesIgnore,
        schemaMode: String = DataReadCore.schemaStrict
    ): ReadPartWorker {
        return ReadPartWorker(
            input, output, role, attributes, workerLocation, DataOpenerLookup(opener), schemaMode)
    }


    private fun part(id: String, role: String = DataRole.main.name): DataPart {
        return DataPart(DataRole(role), DataRef(null, id), null, null)
    }


    private fun dataUnitClassName(): ClassName = ClassName(DataUnit::class.qualifiedName!!)


    private fun payloads(elements: List<*>): List<Any?> =
        elements.map {
            when (it) {
                is DataValue -> JobDataValues.boundary(it)
                else -> error("Unexpected test element: $it")
            }
        }


    private fun laneContext(): JobLaneContext {
        return JobLaneContext(
            BindingSchema.empty, GraphStructure.empty, ReadPartWorker::class.java.classLoader)
    }


    private class BatchInput private constructor(
        private val batches: ArrayDeque<List<Any?>>
    ): ChannelInput<Any?> {
        constructor(units: List<DataUnit>): this(
            ArrayDeque(listOf(units.map(JobDataValues::lift))))


        companion object {
            fun messages(messages: List<DataValue>): BatchInput =
                BatchInput(ArrayDeque(listOf(messages)))
        }


        fun isDrained(): Boolean = batches.isEmpty()


        override suspend fun receiveBatch(): List<Any?>? = batches.removeFirstOrNull()
        override suspend fun receive(): Any? = error("ExpandingTransformWorker reads physical batches")
        override fun iterator(): ChannelInputIterator<Any?> = error("iterator is unused")
    }


    private class FakeOpener(
        private val factories: Map<String, () -> DataCursor>,
        private val inspected: Map<String, DataShape?> = emptyMap()
    ): DataOpener {
        val opened = mutableListOf<String>()
        var inspectCount = 0


        override suspend fun inspectShape(context: DataContext, part: DataPart): DataShape? {
            inspectCount += 1
            return inspected[part.ref.id]
        }


        override suspend fun open(context: DataContext, part: DataPart): DataCursor {
            opened.add(part.ref.id)
            return factories[part.ref.id]?.invoke()
                ?: error("No cursor for ${part.ref.id}")
        }
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
        var checkpointCount = 0
        var blockingCount = 0
        val progressValues = mutableListOf<Map<String, Any?>>()


        override suspend fun checkpoint() {
            checkpointCount += 1
        }
        override suspend fun <R> runBlockingIo(block: () -> R): R {
            blockingCount += 1
            return block()
        }
        override fun scratchDir(): String = error("ReadPart needs no scratch directory")
        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) {
            progressValues.add(value)
        }
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            error("ReadPart hosts no child")
    }


    private class ParkingControl(
        private val parked: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>
    ): CountingControl() {
        override suspend fun checkpoint() {
            super.checkpoint()
            if (checkpointCount == 2) {
                parked.complete(Unit)
                release.await()
            }
        }
    }


    private class CapturingOutput(
        private val sink: MutableList<DataValue> = mutableListOf(),
        private val batchSize: Int = 1024
    ): ChannelOutput<Any?> {
        var closeCount = 0


        override suspend fun send(element: Any?) {
            sink.add(testJobValue(element))
        }
        override suspend fun flush() {}
        override fun batchSize(): Int = batchSize
        override fun close() {
            closeCount += 1
        }
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
