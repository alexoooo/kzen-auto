package tech.kzen.auto.client.objects.ribbon

import tech.kzen.lib.common.api.model.ObjectLocation


@Suppress("unused")
class RibbonGroup(
        private val objectLocation: ObjectLocation,
        private val children: List<RibbonTool>
) {
    fun title(): String {
        return objectLocation.objectPath.name.value
    }
}