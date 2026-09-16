package tech.kzen.auto.server.objects.job.worker.content


/**
 * What is known about a [Content] before its bytes are read, to the extent the format supplies it: a tar
 * header carries name, length and modification time; a streaming zip may not know the length until the entry
 * has been read. [modifiedEpochMillis] is a scalar so the descriptor lifts as a record of scalars.
 */
data class ContentDescriptor(
    val name: String,
    val length: Long?,
    val modifiedEpochMillis: Long?
)
