package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobChannelPorts
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * The run's actual one-way channel topology, manual wires included (unlike
 * [tech.kzen.auto.common.objects.document.job.JobChannelDerivation], which reports only the order-driven pairs):
 * read from the synthesized definition, whose ports are all filled, so each Worker's input ports name the
 * channels it consumes and its output ports the channels it produces. Serves [JobRun]'s upstream-first
 * quiescence rule (content streaming spike CS3).
 */
object JobChannelTopology {
    /** For each Worker, the Workers producing into the channels it consumes (empty for a source). */
    fun upstreamWorkers(
        graphDefinition: GraphDefinition,
        workerLocations: List<ObjectLocation>
    ): Map<ObjectLocation, List<ObjectLocation>> {
        val structure = graphDefinition.graphStructure
        val producers = HashMap<ObjectLocation, MutableList<ObjectLocation>>()
        val consumed = HashMap<ObjectLocation, List<ObjectLocation>>()
        for (worker in workerLocations) {
            val metadata = structure.graphMetadata.get(worker)
                ?: continue
            val inputs = ArrayList<ObjectLocation>()
            for ((attributeName, attributeMetadata) in metadata.attributes.map) {
                val kind = JobChannelPorts.kindOf(attributeMetadata.type)
                    ?: continue
                val channel = channelAt(structure.graphNotation, worker, attributeName)
                    ?: continue
                when (kind) {
                    JobChannelPorts.Kind.Input -> inputs.add(channel)
                    JobChannelPorts.Kind.Output -> producers.getOrPut(channel) { ArrayList() }.add(worker)
                    else -> {}
                }
            }
            consumed[worker] = inputs
        }
        return workerLocations.associateWith { worker ->
            consumed[worker].orEmpty().flatMap { producers[it].orEmpty() }
        }
    }


    private fun channelAt(
        graphNotation: GraphNotation,
        worker: ObjectLocation,
        port: AttributeName
    ): ObjectLocation? {
        val value = graphNotation.firstAttribute(worker, AttributePath.ofName(port))?.asString()
        if (value.isNullOrBlank()) {
            return null
        }
        return graphNotation.coalesce.locateOptional(
            ObjectReference.parse(value), ObjectReferenceHost.ofLocation(worker))
    }
}
