package tech.kzen.auto.client.objects.ribbon

import tech.kzen.lib.common.model.locate.ObjectLocation


//@Suppress("unused")
class RibbonGroup(
        val title: String,
        val archetype: ObjectLocation,
        val children: List<RibbonTool>
)