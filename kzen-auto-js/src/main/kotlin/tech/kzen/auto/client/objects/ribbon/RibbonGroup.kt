package tech.kzen.auto.client.objects.ribbon

import tech.kzen.lib.common.api.model.ObjectLocation


class RibbonGroup(
        private val objectLocation: ObjectLocation
) {
    fun title(): String {
        return objectLocation.objectPath.name.value
    }
}