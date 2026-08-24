package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation


class AttributeDraftStore {
    object Key : BridgeKey<AttributeDraftStore> {
        override fun create(): AttributeDraftStore = AttributeDraftStore()
    }


    private val values = mutableMapOf<Pair<ObjectLocation, AttributePath>, String>()


    fun put(objectLocation: ObjectLocation, attributePath: AttributePath, value: String) {
        values[objectLocation to attributePath] = value
    }


    fun value(objectLocation: ObjectLocation, attributePath: AttributePath): String? {
        return values[objectLocation to attributePath]
    }


    fun remove(objectLocation: ObjectLocation, attributePath: AttributePath, value: String? = null) {
        val key = objectLocation to attributePath
        if (value == null || values[key] == value) {
            values.remove(key)
        }
    }
}
