package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.model.DataRef


class DataOpenerLookup(
    private val plainOpener: DataOpener
) {
    fun openerFor(ref: DataRef): DataOpener {
        val source = ref.source
            ?: return plainOpener
        throw IllegalStateException("provider-bound refs are not supported yet: ${source.value}")
    }
}
