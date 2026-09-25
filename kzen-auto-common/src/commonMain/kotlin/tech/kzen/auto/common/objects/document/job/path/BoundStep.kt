package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.exec.data.type.FieldId


/**
 * A [ProjectionPath] segment resolved against a contract: what the runtime navigates. [Metadata] enters the
 * value's metadata (it is only ever the first step; without it a path starts at the payload). [Elements] and
 * [Entries] are the two unnesting steps (a list's elements, a map's entries); after [Entries] the path
 * continues into the entry's [Key] or [Value].
 */
sealed interface BoundStep {
    data object Metadata: BoundStep

    data class Field(val id: FieldId): BoundStep

    data object Elements: BoundStep

    data object Entries: BoundStep

    data object Key: BoundStep

    data object Value: BoundStep


    val unnests: Boolean
        get() = this is Elements || this is Entries
}
