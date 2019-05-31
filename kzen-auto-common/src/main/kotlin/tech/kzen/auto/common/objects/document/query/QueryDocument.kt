package tech.kzen.auto.common.objects.document.query

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class QueryDocument(
        val vertices: List<Dataflow<*>>,
        val edges: List<EdgeDescriptor>,

        objectLocation: ObjectLocation
):
        DocumentArchetype(objectLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val verticesAttributeName = AttributeName("vertices")
        val verticesAttributePath = AttributePath.ofName(verticesAttributeName)

        val edgesAttributeName = AttributeName("edges")
        val edgesAttributePath = AttributePath.ofName(edgesAttributeName)
    }
}
