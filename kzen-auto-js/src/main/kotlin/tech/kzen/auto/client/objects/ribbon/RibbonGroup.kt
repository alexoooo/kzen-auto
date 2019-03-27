package tech.kzen.auto.client.objects.ribbon

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.api.model.ObjectLocation


@Suppress("unused")
class RibbonGroup(
        private val title: String,
        val documentArchetype: DocumentArchetype,
        private val objectLocation: ObjectLocation,
        private val children: List<RibbonTool>
) {
    fun title(): String {
        return title
    }
}