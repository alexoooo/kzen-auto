package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull


class JobChannelSynthesisTest {
    private val documentPath = DocumentPath.parse("test/job/run/job-trailing-transform-test.yaml")
    private val filter = ObjectLocation(documentPath, ObjectPath.parse("main.workers/Filter"))
    private val graphDefinition = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation()).transitiveSuccessful


    @Test
    fun anOutputNothingConsumesFeedsAnImplicitPreview() {
        val result = JobChannelSynthesis(NotationMetadataReader()).synthesize(graphDefinition, documentPath)

        val preview = ObjectLocation(
            documentPath, JobConventions.implicitPreviewPath(filter.objectPath, AttributeName("output")))
        assertEquals(listOf(preview), result.implicitWorkers)

        val filtered = result.graphDefinition.filterTransitive(documentPath)
        assertNotNull(filtered.objectDefinitions[filter], "the last Worker runs")
        assertNotNull(filtered.objectDefinitions[preview], "and what it produces is sampled")
        assertEquals(
            listOf("ch__source__output", "ch__Filter__output", "ch__pv__Filter__output__serve"),
            result.channelLocations.map { it.objectPath.name.value })

        val savedNotation = graphDefinition.graphStructure.graphNotation
        assertFalse(preview in savedNotation.coalesce, "the Preview exists only in the run copy")
    }
}
