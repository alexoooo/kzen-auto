package tech.kzen.auto.client.objects.ribbon

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class RibbonTool(
        // used structurally by name
        @Suppress("unused")
        val delegate: ObjectLocation
)