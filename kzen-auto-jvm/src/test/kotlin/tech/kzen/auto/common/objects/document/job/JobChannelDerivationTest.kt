package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals


class JobChannelDerivationTest {
    @Test
    fun lastWorkersOutputIsReportedOpenUntilAConsumerIsInserted() {
        val documentPath = DocumentPath.parse("test/job/run/job-trailing-transform-test.yaml")
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation()).transitiveSuccessful

        val derivation = JobChannelDerivation.derive(graphDefinition.graphStructure, documentPath)

        val source = ObjectLocation(documentPath, ObjectPath.parse("main.workers/source"))
        val filter = ObjectLocation(documentPath, ObjectPath.parse("main.workers/Filter"))
        assertEquals(
            listOf(JobChannelDerivation.Connection(source, AttributeName("output"), filter, AttributeName("input"))),
            derivation.connections)
        assertEquals(
            listOf(JobChannelDerivation.OpenOutput(filter, AttributeName("output"))),
            derivation.openOutputs,
            "only the output nothing consumes is open")
    }
}
