package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail


/**
 * A Job whose last Worker's output nothing consumes runs that Worker into an implicit Preview (see
 * [tech.kzen.auto.common.objects.document.job.JobChannelSynthesis]), whose sample the editor reads at the
 * Preview's deterministic location — the same trace read path as a saved Preview.
 */
class JobImplicitPreviewTest {
    private val documentPath = DocumentPath.parse("test/job/run/job-implicit-preview-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val previewLocation = ObjectLocation(
        documentPath,
        JobConventions.implicitPreviewPath(ObjectPath.parse("main.workers/Filter"), AttributeName("output")))

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun lastWorkersOutputIsSampledByAnImplicitPreview() {
        context = KzenAutoContext.forTest()
        val controller = context.serverLogicController
        val attempt = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())

        val runId = controller.start(jobLocation, attempt)
            ?: fail("Unable to start run")
        controller.continueOrStart(runId, attempt)
        runBlocking {
            // Formula compilation runs inside the engine and can exceed the ordinary control wait
            withTimeout(30_000L) {
                while (controller.status().active != null) delay(10L)
            }
        }

        val snapshot = context.logicTrace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: fail("run snapshot not found")
        val progress = snapshot.values[JobConventions.workerProgressPath(
                context.objectStableMapper.objectStableId(previewLocation))]
            ?.value?.get() as? Map<*, *>
            ?: fail("implicit Preview progress not in the run snapshot")

        assertEquals(50L, progress[JobConventions.progressCountKey], "the Filter ran and every survivor reached the Preview")
    }
}
