package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.lib.common.exec.data.value.ValueAccess


/**
 * The [ValueAccess] of a pooled value: the pooled object itself, or its pool slot's access. A value it makes
 * carries [Recyclable.owners], so a re-wrap with new metadata, or a child view, still holds the object through
 * the same resource.
 */
interface RecyclableAccess: ValueAccess {
    val recyclable: Recyclable
}
