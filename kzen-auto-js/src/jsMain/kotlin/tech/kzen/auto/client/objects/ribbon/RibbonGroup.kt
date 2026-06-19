package tech.kzen.auto.client.objects.ribbon

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
data class RibbonGroup(
    val title: String,
    val archetype: ObjectLocation,
    // Empty for normal action groups. When non-empty, selecting this tab switches the document stage to
    // the named view (e.g. "Raw") instead of offering insertion actions — published via the bridge's ViewModeKey.
    val viewMode: String,
    val children: List<RibbonTool>
)