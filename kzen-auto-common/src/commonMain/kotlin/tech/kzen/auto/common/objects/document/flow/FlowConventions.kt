package tech.kzen.auto.common.objects.document.flow

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Conventions for the Flow document (the modernized "graph" / "time series", run on the Logic model).
 * Reuses the dataflow `vertices` / `edges` attribute shape so the existing
 * [tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix] reads it unchanged.
 */
object FlowConventions {
    val objectName = ObjectName("Flow")

    val verticesAttributeName = AttributeName("vertices")
    val verticesAttributePath = AttributePath.ofName(verticesAttributeName)

    val edgesAttributeName = AttributeName("edges")
    val edgesAttributePath = AttributePath.ofName(edgesAttributeName)


    fun isFlow(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == objectName.value
    }
}
