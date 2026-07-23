package tech.kzen.auto.server.api.handler

import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


class ObjectStableHandler(
    private val objectStableMapper: ObjectStableMapper
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun objectStableMapperSnapshot(): Map<String, String> {
        val snapshot = objectStableMapper.snapshot()
        return snapshot.entries.associate { (id, location) ->
            id.value to location.asString()
        }
    }
}
