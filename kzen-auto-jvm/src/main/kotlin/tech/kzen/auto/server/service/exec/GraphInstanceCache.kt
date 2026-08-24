package tech.kzen.auto.server.service.exec

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.ObjectCreationFailure
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.util.digest.Digest


/**
 * Digest-keyed cache of graph-instantiated server objects, each built from its own transitive
 * definition closure instead of the whole project. Callers pass the already-policy-filtered
 * [GraphDefinition] (serverAllowed filter first, closure second - never instantiate client-only
 * objects server-side); the cache itself is policy-agnostic.
 *
 * Key: the closure's notation digest ([GraphDefinition.transitiveDigest]) combined with each closure
 * member's inheritance-chain notation digests - the chain digests cover inherited-value edits on
 * user-editable prototypes, which the closure digest alone misses (ancestors are not definition
 * references). Reuse requires cached objects to be stateless per the DetachedAction /
 * DetachedDownloadAction / ManagedTask contract; an archetype opts out by declaring
 * `instanceCaching: "false"`, which yields a fresh instance per request.
 */
class GraphInstanceCache(
    private val graphCreator: GraphCreator,
    private val environment: GraphEnvironment,
    private val maxEntries: Int = defaultMaxEntries,
    private val honorInstanceCachingOptOut: Boolean = true
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Bounds entries left behind by renamed / deleted objects. Cached instances hold no resources
        // (statelessness contract), so eviction needs no disposal hook.
        private const val defaultMaxEntries = 32

        private val logger = LoggerFactory.getLogger(GraphInstanceCache::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class Entry(
        val digest: Digest,
        val objectInstance: ObjectInstance)


    private val entries = object: LinkedHashMap<ObjectLocation, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ObjectLocation, Entry>): Boolean {
            return size > maxEntries
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Null when [objectLocation] has no (successful, policy-allowed) definition in [definition], or when its
     *  creation failed - see [tryObjectInstance] for which of the two, and why.
     */
    fun objectInstance(
        definition: GraphDefinition,
        objectLocation: ObjectLocation
    ): ObjectInstance? {
        return (tryObjectInstance(definition, objectLocation) as? ObjectInstanceAttempt.Created)
            ?.objectInstance
    }


    @Synchronized
    fun tryObjectInstance(
        definition: GraphDefinition,
        objectLocation: ObjectLocation
    ): ObjectInstanceAttempt {
        if (objectLocation !in definition.objectDefinitions) {
            // NB: guard before transitiveDigest / filterTransitive, which require a present seed
            return ObjectInstanceAttempt.Undefined
        }

        if (honorInstanceCachingOptOut && cachingOptedOut(definition, objectLocation)) {
            // an archetype that just gained the opt-out takes effect immediately, regardless of digest
            entries.remove(objectLocation)
            return create(definition, objectLocation)
        }

        val digest = cacheKey(definition, objectLocation)

        // access-ordered get, so a hit is also an LRU touch
        val cached = entries[objectLocation]
        if (cached != null && cached.digest == digest) {
            return ObjectInstanceAttempt.Created(cached.objectInstance)
        }

        val attempt = create(definition, objectLocation)

        // failures are rare and recompute cheaply - only successful instances are worth a cache entry
        if (attempt is ObjectInstanceAttempt.Created) {
            entries[objectLocation] = Entry(digest, attempt.objectInstance)
        }

        return attempt
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun create(definition: GraphDefinition, objectLocation: ObjectLocation): ObjectInstanceAttempt {
        val scoped = definition.filterTransitive(objectLocation)

        val startNanos = System.nanoTime()
        val instanceAttempt = graphCreator.tryCreateGraph(scoped, environment)

        logger.debug("built {} - {} of {} definitions in {}us",
            objectLocation, scoped.objectDefinitions.map.size, definition.objectDefinitions.map.size,
            (System.nanoTime() - startNanos) / 1_000)

        val objectInstance = instanceAttempt.objectInstances[objectLocation]
        if (objectInstance != null) {
            return ObjectInstanceAttempt.Created(objectInstance)
        }

        return ObjectInstanceAttempt.Failed(
            instanceAttempt.failures[objectLocation]
                ?: ObjectCreationFailure("Not created"))
    }


    // Closure digest plus the closure members' inheritance-chain notation digests (see class kdoc).
    internal fun cacheKey(definition: GraphDefinition, objectLocation: ObjectLocation): Digest {
        val closureDigest = definition.transitiveDigest(listOf(objectLocation))
        val closure = definition.transitiveClosure(listOf(objectLocation))
        val graphNotation = definition.graphStructure.graphNotation

        return Digest.build {
            addDigest(closureDigest)

            for (member in closure.sortedBy { it.asString() }) {
                if (graphNotation.coalesce[member] == null) {
                    // synthesized member: no notation, no inheritance chain
                    continue
                }

                for (ancestor in graphNotation.inheritanceChain(member)) {
                    addDigestible(ancestor)
                    addDigestibleNullable(graphNotation.coalesce[ancestor])
                }
            }
        }
    }


    private fun cachingOptedOut(definition: GraphDefinition, objectLocation: ObjectLocation): Boolean {
        val attributeNotation = definition
            .graphStructure
            .graphNotation
            .firstAttribute(objectLocation, AutoConventions.instanceCachingAttributePath)

        return (attributeNotation as? ScalarAttributeNotation)?.value == "false"
    }
}
