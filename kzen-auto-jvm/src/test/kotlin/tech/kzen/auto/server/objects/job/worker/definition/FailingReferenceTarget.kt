package tech.kzen.auto.server.objects.job.worker.definition

import tech.kzen.lib.common.reflect.Reflect


@Reflect
class FailingReferenceTarget {
    init {
        error("reference target creation failure")
    }
}
