package tech.kzen.auto.common.objects.document

import tech.kzen.auto.common.paradigm.dataflow.Dataflow
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class QueryDocument(
//        val sources: List<ExecutionAction>,
        val vertices: List<Dataflow>,
        objectLocation: ObjectLocation
):
        DocumentArchetype(objectLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val sourcesAttributePath = AttributePath.parse("sources")
        val verticesAttributeName = AttributeName("vertices")
        val verticesAttributePath = AttributePath.ofName(verticesAttributeName)
    }
}
