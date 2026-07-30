package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobChannelSynthesis
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectCommand
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * How a Job Channel's `batchSize` / `capacity` reach [JobChannelSynthesis]:
 *  - the Job-wide defaults declared on `main` are stamped onto every auto-synthesized channel (the common path
 *    carries no `is: Channel` object, so a channel would otherwise only ever get the archetype defaults 1024/0);
 *  - per-channel config lives on the UPSTREAM Worker in its `channels.<outputPort>` map, so it wins over the
 *    Job-wide default AND follows the Worker across a rename — there is no name-coupled override object.
 */
class JobChannelDefaultTest {
    @Test
    fun jobWideDefaultsAreStampedOntoSynthesizedChannels() {
        val documentPath = DocumentPath.parse("test/job/channel/job-batchsize-default-test.yaml")

        val graphNotation = AutoTestUtils.readNotation()
        val result = synthesize(graphNotation, documentPath)
        val augmentedNotation = result.graphDefinition.graphStructure.graphNotation

        // Two adjacent-Worker connections (reader->filter, filter->writer) => two synthesized one-way channels.
        assertEquals(2, result.channelLocations.size, "expected two synthesized channels")

        for (channelLocation in result.channelLocations) {
            assertEquals(
                "32", channelValue(augmentedNotation, channelLocation, JobConventions.batchSizeAttributeName),
                "batchSize on $channelLocation")
            assertEquals(
                "4", channelValue(augmentedNotation, channelLocation, JobConventions.capacityAttributeName),
                "capacity on $channelLocation")
        }

        assertTrue(result.channelLocations.all { it.objectPath.name.value.startsWith("ch__") })
    }


    @Test
    fun perWorkerOutputConfigStampedOntoSynthesizedChannel() {
        val documentPath = DocumentPath.parse("test/job/channel/job-worker-config-test.yaml")

        val graphNotation = AutoTestUtils.readNotation()
        val result = synthesize(graphNotation, documentPath)
        val augmentedNotation = result.graphDefinition.graphStructure.graphNotation

        // reader declares batchSize 7 / capacity 3 on its output => the reader->filter channel carries them.
        val readerChannel = channelLocation(documentPath, "ch__reader__output")
        assertEquals("7", channelValue(augmentedNotation, readerChannel, JobConventions.batchSizeAttributeName))
        assertEquals("3", channelValue(augmentedNotation, readerChannel, JobConventions.capacityAttributeName))

        // filter declares no config => its output channel falls back to the Job-wide default (1024 / 0).
        val filterChannel = channelLocation(documentPath, "ch__filter__output")
        assertEquals("1024", channelValue(augmentedNotation, filterChannel, JobConventions.batchSizeAttributeName))
        assertEquals("0", channelValue(augmentedNotation, filterChannel, JobConventions.capacityAttributeName))

        // The reader's output port was wired to its channel in the run copy (auto-wiring still holds).
        val readerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/reader"))
        val outputRef = augmentedNotation.firstAttribute(
            readerLocation, AttributePath.ofName(AttributeName("output")))?.asString()
        assertTrue(
            outputRef != null && outputRef.contains("ch__reader__output"),
            "reader.output wired to its channel: $outputRef")

        assertEquals(2, result.channelLocations.size)
    }


    @Test
    fun workerOutputConfigSurvivesUpstreamWorkerRename() {
        val documentPath = DocumentPath.parse("test/job/channel/job-worker-config-test.yaml")

        val graphNotation = AutoTestUtils.readNotation()
        val readerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/reader"))

        // Rename the upstream Worker: because its batchSize / capacity live ON the Worker, they move with it —
        // the renamed Worker's channel (ch__loader__output) must still carry 7 / 3. A name-coupled override
        // object would instead have been orphaned by the rename, reverting the channel to defaults.
        val renamedNotation = NotationReducer()
            .applyStructural(graphNotation, RenameObjectCommand(readerLocation, ObjectName("loader")))
            .graphNotation

        val result = synthesize(renamedNotation, documentPath)
        val augmentedNotation = result.graphDefinition.graphStructure.graphNotation

        val loaderChannel = channelLocation(documentPath, "ch__loader__output")
        assertEquals("7", channelValue(augmentedNotation, loaderChannel, JobConventions.batchSizeAttributeName))
        assertEquals("3", channelValue(augmentedNotation, loaderChannel, JobConventions.capacityAttributeName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun synthesize(graphNotation: GraphNotation, documentPath: DocumentPath): JobChannelSynthesis.Result {
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        return JobChannelSynthesis(NotationMetadataReader()).synthesize(graphDefinition, documentPath)
    }


    private fun channelLocation(documentPath: DocumentPath, channelName: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse("main.channels/$channelName"))
    }


    private fun channelValue(
        graphNotation: GraphNotation,
        channelLocation: ObjectLocation,
        attributeName: AttributeName
    ): String? {
        return graphNotation.firstAttribute(channelLocation, AttributePath.ofName(attributeName))?.asString()
    }
}
