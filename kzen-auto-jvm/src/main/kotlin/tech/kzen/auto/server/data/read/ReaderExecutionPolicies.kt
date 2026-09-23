package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.read.InspectionPolicy
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import kotlin.time.Duration.Companion.milliseconds


/**
 * A full run reads the whole content however large it expands or long it takes, and ends on cancellation; its
 * memory is bounded per record and field instead. Inspection reads a sample, so its bytes and time are bounded.
 */
data class ReaderExecutionPolicies(
    val run: ReadOperationalPolicy = ReadOperationalPolicy(
        maximumRecordCharacters = 1024 * 1024,
        maximumFieldCharacters = 1024 * 1024,
        maximumFields = 10_000),
    val inspection: InspectionPolicy = InspectionPolicy(
        maximumRecords = 100,
        maximumExpandedBytes = 8L * 1024 * 1024,
        timeoutMillis = 30_000)
) {
    init {
        require(
            inspection.maximumRecords != null &&
                inspection.maximumExpandedBytes != null &&
                inspection.timeoutMillis != null
        ) { "Inspection record, expanded-byte, and timeout limits must be effective" }
    }

    val runContent: ContentReadPolicy get() = ContentReadPolicy.of(run, Long.MAX_VALUE)

    val inspectionContent: ContentReadPolicy get() = ContentReadPolicy(
        requireNotNull(inspection.maximumExpandedBytes),
        requireNotNull(inspection.timeoutMillis).milliseconds,
        requireNotNull(inspection.maximumRecords).toLong())
}
