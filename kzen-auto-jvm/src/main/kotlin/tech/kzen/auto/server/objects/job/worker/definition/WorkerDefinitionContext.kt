package tech.kzen.auto.server.objects.job.worker.definition

import tech.kzen.auto.server.service.exec.GraphInstanceCache
import tech.kzen.auto.server.service.exec.ObjectInstanceAttempt
import tech.kzen.auto.server.service.impl.LinkedLogicDocuments
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.util.digest.Digest


/**
 * Resolves a Worker's nominal dependencies from one compiled graph snapshot. Same-document objects reuse the
 * run graph; cross-document objects are instantiated once through this context's cache from that same snapshot.
 */
class WorkerDefinitionContext(
    private val definition: GraphDefinition,
    private val runInstance: GraphInstance,
    environment: GraphEnvironment
) {
    // A run snapshot is finite and short-lived. Do not evict within it: resolving more than the service cache's
    // normal LRU bound must still instantiate each cross-document nominal dependency at most once for this run.
    private val instanceCache = GraphInstanceCache(
        GraphCreator, environment, Int.MAX_VALUE, honorInstanceCachingOptOut = false)


    internal fun graphStructure(): GraphStructure = definition.graphStructure


    /** Includes both the instantiated definition closure and weakly linked hosted-Logic callees. */
    fun definitionDependencyDigest(location: ObjectLocation): Digest = Digest.build {
        addDigest(instanceCache.cacheKey(definition, location))
        addDigest(LinkedLogicDocuments.transitiveDigest(
            definition, definition.graphStructure, location.documentPath))
    }


    fun resolve(reference: ObjectReference, host: ObjectLocation): WorkerDefinitionResolution {
        val location = try {
            definition.objectDefinitions.locate(
                reference, ObjectReferenceHost.ofLocation(host))
        }
        catch (e: IllegalArgumentException) {
            return WorkerDefinitionResolution.Failed(
                "Unable to resolve '$reference' from $host: ${e.message}")
        }

        val cacheKey = try {
            instanceCache.cacheKey(definition, location)
        }
        catch (e: IllegalArgumentException) {
            return WorkerDefinitionResolution.Failed(
                "Unable to prepare '$reference' at $location: ${e.message}")
        }

        val value =
            if (location.documentPath == host.documentPath) {
                runInstance[location]?.reference
                    ?: return WorkerDefinitionResolution.Failed(
                        "Referenced object was not created: $location")
            }
            else {
                when (val attempt = instanceCache.tryObjectInstance(definition, location)) {
                    is ObjectInstanceAttempt.Created -> attempt.objectInstance.reference
                    is ObjectInstanceAttempt.Failed ->
                        return WorkerDefinitionResolution.Failed(
                            "Unable to create referenced object $location: ${attempt.failure.errorMessage}")
                    ObjectInstanceAttempt.Undefined ->
                        return WorkerDefinitionResolution.Failed(
                            "Referenced object is not defined: $location")
                }
            }

        return WorkerDefinitionResolution.Resolved(location, cacheKey, value)
    }
}
