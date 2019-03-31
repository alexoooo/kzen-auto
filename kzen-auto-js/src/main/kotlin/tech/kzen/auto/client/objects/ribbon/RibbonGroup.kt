package tech.kzen.auto.client.objects.ribbon

import tech.kzen.auto.common.objects.document.DocumentArchetype


//@Suppress("unused")
class RibbonGroup(
        val title: String,
        val documentArchetype: DocumentArchetype,
        val children: List<RibbonTool>
)