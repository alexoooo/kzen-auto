package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.read.InspectionPolicy
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes


data class ReaderExecutionPolicies(
    val run: ReadOperationalPolicy = ReadOperationalPolicy(
        maximumExpandedBytes = 256L * 1024 * 1024,
        maximumRecordCharacters = 1024 * 1024,
        maximumFieldCharacters = 1024 * 1024,
        maximumFields = 10_000,
        timeoutMillis = 5.minutes.inWholeMilliseconds),
    val inspection: InspectionPolicy = InspectionPolicy(
        maximumRecords = 100,
        maximumExpandedBytes = 8L * 1024 * 1024,
        timeoutMillis = 30_000)
) {
    init {
        require(run.maximumExpandedBytes != null && run.timeoutMillis != null) {
            "Run expanded-byte and timeout limits must be effective"
        }
        require(
            inspection.maximumRecords != null &&
                inspection.maximumExpandedBytes != null &&
                inspection.timeoutMillis != null
        ) { "Inspection record, expanded-byte, and timeout limits must be effective" }
    }

    val runContent: ContentReadPolicy get() = ContentReadPolicy(
        requireNotNull(run.maximumExpandedBytes),
        requireNotNull(run.timeoutMillis).milliseconds,
        Long.MAX_VALUE)

    val inspectionContent: ContentReadPolicy get() = ContentReadPolicy(
        requireNotNull(inspection.maximumExpandedBytes),
        requireNotNull(inspection.timeoutMillis).milliseconds,
        requireNotNull(inspection.maximumRecords).toLong())
}
