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
 * [JobServeCapability] classifies a Worker's interactive capability from the semantic type of its `serve` port,
 * replacing the removed concrete-Worker-name string gates (CC-17). Loads a real notation graph and asserts each
 * built-in serve-bearing Worker classifies correctly, a non-serving sink is null, and — the extensibility proof —
 * a Worker whose serve port is a user-defined `PreviewServer` subtype classifies `Preview` with no code change.
 */
class JobServeCapabilityTest {
    companion object {
        private val documentPath = DocumentPath.parse("test/job-serve-capability-test.yaml")

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
    fun previewWorkerServesPreview() {
        assertEquals(JobServeCapability.Capability.Preview, capabilityOf("preview"))
    }


    @Test
    fun summaryWorkerServesSummary() {
        assertEquals(JobServeCapability.Capability.Summary, capabilityOf("summary"))
    }


    @Test
    fun exploreWorkerServesTable() {
        assertEquals(JobServeCapability.Capability.Table, capabilityOf("explore"))
    }


    @Test
    fun pivotWorkerServesPreview() {
        // Pivot serves preview slices — the name-based gate silently omitted it; capability classification includes it.
        assertEquals(JobServeCapability.Capability.Preview, capabilityOf("pivot"))
    }


    @Test
    fun sinkWithoutServeHasNoCapability() {
        assertNull(capabilityOf("writer"))
    }


    @Test
    fun thirdPartyServeSubtypeIsRecognizedWithoutCodeChange() {
        // The whole point of CC-17: a Worker declaring a user-defined PreviewServer subtype gets the Preview
        // capability purely through the inheritance chain — no edit to JobServeCapability's enum or logic.
        assertEquals(JobServeCapability.Capability.Preview, capabilityOf("customPreview"))
    }
}
