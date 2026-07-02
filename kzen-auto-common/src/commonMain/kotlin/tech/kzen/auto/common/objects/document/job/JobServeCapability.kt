package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Classifies whether a Worker serves a live summary — from the semantic type of its `serve` port (a SummaryServer,
 * a ChannelServer subtype) — so the summary-aware editors (value-set filter, pivot) can find their upstream
 * distinct-value source WITHOUT enumerating concrete Worker class names (see CC-17). Classification walks the serve
 * port archetype's inheritance CHAIN, mirroring [JobConventions.isChannelArchetype], so a 3rd-party subtype of
 * SummaryServer is recognized with no code change. Pure over notation + metadata.
 *
 * The serve port's resolved [type] className stays `ChannelServer` (SummaryServer carries no own `class:`), so the
 * server-side wiring (JobChannelPorts / JobChannelCreator / JobChannelSynthesis) is untouched — only this client
 * classification reads the declared subtype. Card rendering (preview sample / download) is NOT classified here:
 * each Worker contributes its own card via the `display:` marker (see WorkerDisplayManager), so only the
 * cross-cutting summary-source discovery — a dataflow concern a Worker cannot own — remains capability-based.
 */
object JobServeCapability {
    enum class Capability(val serverArchetype: ObjectName) {
        // Serves a live TableSummary — the upstream distinct-value source for the filter / pivot editors.
        Summary(JobConventions.summaryServerObjectName)
    }


    // The capability the Worker's serve port declares, or null when it has no serve port (or a bare
    // ChannelServer opting into no capability). Reads the serve port's declared archetype from metadata and
    // returns the Capability whose marker archetype the port's inheritance chain reaches.
    fun of(graphStructure: GraphStructure, workerLocation: ObjectLocation): Capability? {
        val objectMetadata = graphStructure.graphMetadata.get(workerLocation)
            ?: return null
        val graphNotation = graphStructure.graphNotation

        for ((_, attributeMetadata) in objectMetadata.attributes.map) {
            if (JobChannelPorts.kindOf(attributeMetadata.type) != JobChannelPorts.Kind.Server) {
                continue
            }

            val declaredArchetype = attributeMetadata.attributeMetadataNotation
                .map[NotationConventions.isAttributeSegment]
                ?.asString()
                ?: continue

            val archetypeLocation = graphNotation.coalesce.locateOptional(
                ObjectReference.parse(declaredArchetype),
                ObjectReferenceHost.ofLocation(workerLocation))
                ?: continue

            val chainNames = graphNotation.inheritanceChain(archetypeLocation)
                .mapTo(mutableSetOf()) { it.objectPath.name }

            val capability = Capability.entries.firstOrNull { it.serverArchetype in chainNames }
            if (capability != null) {
                return capability
            }
        }

        return null
    }
}
