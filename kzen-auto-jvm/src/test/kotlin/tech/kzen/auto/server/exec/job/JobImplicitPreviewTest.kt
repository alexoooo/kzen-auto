package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail


/**
 * A Job whose last Worker's output nothing consumes runs that Worker into an implicit Preview (see
 * [tech.kzen.auto.common.objects.document.job.JobChannelSynthesis]), whose sample the editor reads at the
 * Preview's deterministic location — the same trace read path as a saved Preview. A saved Preview is a
 * pass-through: between two Workers it forwards everything, and ending a Job its optional output gets no
 * implicit Preview of its own.
 */
class JobImplicitPreviewTest {
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun lastWorkersOutputIsSampledByAnImplicitPreview() {
        val documentPath = DocumentPath.parse("test/job/run/job-implicit-preview-test.yaml")
        val snapshot = run(documentPath)

        assertEquals(50L, count(snapshot, implicitPreview(documentPath, "Filter")),
            "the Filter ran and every survivor reached the Preview")
    }


    @Test
    fun previewBetweenWorkersForwardsEverything() {
        val documentPath = DocumentPath.parse("test/job/run/job-preview-between-test.yaml")
        val snapshot = run(documentPath)

        assertEquals(100L, count(snapshot, saved(documentPath, "Peek")), "the Preview sampled every element")
        assertEquals(50L, count(snapshot, implicitPreview(documentPath, "Filter")),
            "the Preview forwarded every element to the Filter")
    }


    @Test
    fun trailingPreviewGetsNoImplicitPreview() {
        val documentPath = DocumentPath.parse("test/job/run/job-preview-trailing-test.yaml")
        val snapshot = run(documentPath)

        assertEquals(50L, count(snapshot, saved(documentPath, "Last")))
        assertNull(progress(snapshot, implicitPreview(documentPath, "Last")),
            "an optional output nothing consumes is not sampled again")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun run(documentPath: DocumentPath): LogicTraceSnapshot {
        context = KzenAutoContext.forTest()
        val controller = context.serverLogicController
        val attempt = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())

        val runId = controller.start(ObjectLocation(documentPath, ObjectPath.parse("main")), attempt)
            ?: fail("Unable to start run")
        controller.continueOrStart(runId, attempt)
        runBlocking {
            // Formula compilation runs inside the engine and can exceed the ordinary control wait
            withTimeout(120_000L) {
                while (controller.status().active != null) delay(10L)
            }
        }

        return context.logicTrace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: fail("run snapshot not found")
    }


    private fun saved(documentPath: DocumentPath, worker: String): ObjectLocation =
        ObjectLocation(documentPath, ObjectPath.parse("main.workers/$worker"))


    private fun implicitPreview(documentPath: DocumentPath, worker: String): ObjectLocation =
        ObjectLocation(
            documentPath,
            JobConventions.implicitPreviewPath(ObjectPath.parse("main.workers/$worker"), AttributeName("output")))


    private fun progress(snapshot: LogicTraceSnapshot, worker: ObjectLocation): Map<*, *>? =
        snapshot.values[JobConventions.workerProgressPath(context.objectStableMapper.objectStableId(worker))]
            ?.value?.get() as? Map<*, *>


    private fun count(snapshot: LogicTraceSnapshot, worker: ObjectLocation): Any? =
        (progress(snapshot, worker) ?: fail("no progress for $worker in the run snapshot"))[
            JobConventions.progressCountKey]
}
