package tech.kzen.auto.common.objects.document.plugin

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object PluginConventions {
    private val pluginObjectName = ObjectName("Plugin")

    val jarPathAttributeName = AttributeName("jarPath")


    fun isPlugin(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == pluginObjectName.value
    }
}