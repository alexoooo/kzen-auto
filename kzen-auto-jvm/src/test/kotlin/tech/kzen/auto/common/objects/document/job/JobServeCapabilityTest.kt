package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * [JobServeCapability] classifies whether a Worker serves a live summary from the semantic type of its `serve`
 * port, so the summary-aware editors (value-set filter / pivot) can find their upstream distinct-value source
 * without naming concrete Worker classes (CC-17). Loads a real notation graph and asserts the SummaryWorker
 * classifies Summary, non-summary serve-bearing Workers and a non-serving sink are null, and — the extensibility
 * proof — a Worker whose serve port is a user-defined SummaryServer subtype classifies Summary with no code change.
 * Card rendering (preview sample / download) is chosen by each Worker's own `display:` marker, not classified here.
 */
class JobServeCapabilityTest {
    companion object {
        private val documentPath = DocumentPath.parse("test/job/job-serve-capability-test.yaml")

        private val graphStructure: GraphStructure by lazy {
            val graphNotation = AutoTestUtils.readNotation()
            GraphStructure(graphNotation, AutoTestUtils.graphMetadata(graphNotation))
        }
    }


    private fun capabilityOf(workerName: String): JobServeCapability.Capability? {
        val workerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/$workerName"))
        return JobServeCapability.of(graphStructure, workerLocation)
    }


    @Test
    fun summaryWorkerServesSummary() {
        assertEquals(JobServeCapability.Capability.Summary, capabilityOf("summary"))
    }


    @Test
    fun previewWorkerIsNotASummarySource() {
        // Serves a preview sample, but that is a card behaviour (its `display:` marker), not a summary source.
        assertNull(capabilityOf("preview"))
    }


    @Test
    fun exploreWorkerIsNotASummarySource() {
        assertNull(capabilityOf("explore"))
    }


    @Test
    fun pivotWorkerIsNotASummarySource() {
        assertNull(capabilityOf("pivot"))
    }


    @Test
    fun sinkWithoutServeHasNoCapability() {
        assertNull(capabilityOf("writer"))
    }


    @Test
    fun thirdPartySummarySubtypeIsRecognizedWithoutCodeChange() {
        // The whole point of CC-17: a Worker declaring a user-defined SummaryServer subtype is recognized as a
        // summary source purely through the inheritance chain — no edit to JobServeCapability's enum or logic.
        assertEquals(JobServeCapability.Capability.Summary, capabilityOf("customSummary"))
    }
}
