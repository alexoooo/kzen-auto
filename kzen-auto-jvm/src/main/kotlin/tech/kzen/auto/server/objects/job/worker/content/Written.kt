package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.common.data.model.DataRef


/**
 * One published file (design §7): its [name] (for a file written from a content, the content's name; for bytes,
 * its path below the directory), the [ref] of the published destination and its [size] in bytes on disk. A lifted
 * literal record with no native behind it, so it leaves the scope freely.
 */
data class Written(
    val name: String,
    val ref: DataRef,
    val size: Long
)
