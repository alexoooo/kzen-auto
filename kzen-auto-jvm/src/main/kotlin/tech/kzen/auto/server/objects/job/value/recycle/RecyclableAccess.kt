package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.lib.common.exec.data.value.ValueAccess


/**
 * The [ValueAccess] of a pooled value: the pooled object itself, or its pool slot's access. A value re-wrapped
 * with new metadata, or navigated to a child, shares the access, so it is still found by [Recyclable.of].
 */
interface RecyclableAccess: ValueAccess {
    val recyclable: Recyclable
}
