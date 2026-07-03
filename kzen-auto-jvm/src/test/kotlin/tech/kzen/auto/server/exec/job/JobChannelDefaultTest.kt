package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobChannelSynthesis
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * How a Job Channel's `batchSize` / `capacity` reach [JobChannelSynthesis]:
 *  - the Job-wide defaults declared on `main` are stamped onto every auto-synthesized channel (the common path
 *    carries no `is: Channel` object, so a channel would otherwise only ever get the archetype defaults 1024/0);
 *  - a per-channel override — a materialized `is: Channel` object at the deterministic synth name with the Worker
 *    ports left open — is adopted as-is (ensureChannel is idempotent by object path) with its ports filled only
 *    in the run copy, so the override survives AND order-driven auto-wiring still holds.
 */
class JobChannelDefaultTest {
    @Test
    fun jobWideDefaultsAreStampedOntoSynthesizedChannels() {
        val documentPath = DocumentPath.parse("test/job-batchsize-default-test.yaml")

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val result = JobChannelSynthesis(NotationMetadataReader())
            .synthesize(graphDefinition, documentPath)

        val augmentedNotation = result.graphDefinition.graphStructure.graphNotation

        // Two adjacent-Worker connections (reader->filter, filter->writer) => two synthesized one-way channels.
        assertEquals(2, result.channelLocations.size, "expected two synthesized channels")

        for (channelLocation in result.channelLocations) {
            assertEquals(
                "32",
                augmentedNotation.firstAttribute(
                    channelLocation, AttributePath.ofName(JobConventions.batchSizeAttributeName))?.asString(),
                "batchSize on $channelLocation")
            assertEquals(
                "4",
                augmentedNotation.firstAttribute(
                    channelLocation, AttributePath.ofName(JobConventions.capacityAttributeName))?.asString(),
                "capacity on $channelLocation")
        }

        assertTrue(result.channelLocations.all { it.objectPath.name.value.startsWith("ch__") })
    }


    @Test
    fun perChannelOverrideObjectIsAdoptedBySynthesis() {
        val documentPath = DocumentPath.parse("test/job-channel-override-test.yaml")

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val result = JobChannelSynthesis(NotationMetadataReader())
            .synthesize(graphDefinition, documentPath)

        val augmentedNotation = result.graphDefinition.graphStructure.graphNotation
        val overrideLocation = ObjectLocation(documentPath, ObjectPath.parse("main.channels/ch__reader__output"))

        // The override object's own batchSize / capacity survive — NOT overwritten by the Job/archetype default.
        assertEquals(
            "7",
            augmentedNotation.firstAttribute(
                overrideLocation, AttributePath.ofName(JobConventions.batchSizeAttributeName))?.asString())
        assertEquals(
            "3",
            augmentedNotation.firstAttribute(
                overrideLocation, AttributePath.ofName(JobConventions.capacityAttributeName))?.asString())

        // The reader's output port was wired to the override channel in the run copy (auto-wiring still holds).
        val readerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/reader"))
        val outputRef = augmentedNotation.firstAttribute(
            readerLocation, AttributePath.ofName(AttributeName("output")))?.asString()
        assertTrue(
            outputRef != null && outputRef.contains("ch__reader__output"),
            "reader.output wired to the override channel: $outputRef")

        // Two channels total: the adopted override (reused, not duplicated) plus the filter->writer synth channel.
        assertEquals(2, result.channelLocations.size)
    }
}
