package tech.kzen.auto.client.objects.ribbon

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
data class RibbonGroup(
    val title: String,
    val archetype: ObjectLocation,
    val children: List<RibbonTool>
)