package tech.kzen.auto.server.service.exec

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame


/**
 * Offline coverage of the digest-keyed instance reuse: edits are applied through [NotationReducer]
 * against a read-only notation snapshot (never through a store, which would write real files), and
 * each case asserts whether the resulting definition yields the same instance or a rebuilt one.
 */
class GraphInstanceCacheTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val cachedAction = ObjectLocation.parse("test/detached-cache-test.yaml#CachedAction")
    private val cachedArchetype = ObjectLocation.parse("test/detached-cache-test.yaml#CachedActionArchetype")
    private val cacheNamed = ObjectLocation.parse("test/detached-cache-test.yaml#CacheNamed")
    private val freshAction = ObjectLocation.parse("test/detached-cache-test.yaml#FreshAction")
    private val unrelated = ObjectLocation.parse("test/flow-run-test.yaml#FrunInput")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unchangedNotationReusesTheInstance() {
        val cache = newCache()
        val baseNotation = AutoTestUtils.readNotation()

        // distinct GraphDefinition objects rebuilt from the same notation - equal digest, one instance
        val first = cache.objectInstance(definitionOf(baseNotation), cachedAction)
        val second = cache.objectInstance(definitionOf(baseNotation), cachedAction)

        assertSame(first?.reference, second?.reference)
        assertEquals(ExecutionValue.of("Hello: cache-test"), execute(first?.reference))
    }


    @Test
    fun ownNotationEditRebuilds() {
        assertRebuilds(cachedAction) {
            edit(it, cachedAction, "title", ScalarAttributeNotation("edited"))
        }
    }


    @Test
    fun closureMemberEditRebuilds() {
        val cache = newCache()
        val baseNotation = AutoTestUtils.readNotation()

        val first = cache.objectInstance(definitionOf(baseNotation), cachedAction)

        val editedNotation = edit(baseNotation, cacheNamed, "name", ScalarAttributeNotation("renamed"))
        val second = cache.objectInstance(definitionOf(editedNotation), cachedAction)

        assertNotSame(first?.reference, second?.reference)
        assertEquals(ExecutionValue.of("Hello: renamed"), execute(second?.reference))
    }


    @Test
    fun inheritanceAncestorEditRebuilds() {
        // The archetype is NOT a definition reference of its instance (inheritance is flattened at
        // define time), so this case fails if the cache key drops back to the bare closure digest.
        assertRebuilds(cachedAction) {
            edit(it, cachedArchetype, "title", ScalarAttributeNotation("edited archetype"))
        }
    }


    @Test
    fun unrelatedEditReusesTheInstance() {
        val cache = newCache()
        val baseNotation = AutoTestUtils.readNotation()

        val first = cache.objectInstance(definitionOf(baseNotation), cachedAction)

        val editedNotation = edit(baseNotation, unrelated, "parameter", ScalarAttributeNotation("y"))
        val second = cache.objectInstance(definitionOf(editedNotation), cachedAction)

        assertSame(first?.reference, second?.reference)
    }


    @Test
    fun optedOutArchetypeAlwaysRebuilds() {
        val cache = newCache()
        val definition = definitionOf(AutoTestUtils.readNotation())

        val first = cache.objectInstance(definition, freshAction)
        val second = cache.objectInstance(definition, freshAction)
        val third = cache.objectInstance(definition, freshAction)

        assertNotSame(first?.reference, second?.reference)
        assertNotSame(second?.reference, third?.reference)
        assertEquals(ExecutionValue.of("Hello: cache-test"), execute(third?.reference))
    }


    @Test
    fun unknownLocationIsNull() {
        val definition = definitionOf(AutoTestUtils.readNotation())
        val missing = ObjectLocation.parse("test/detached-cache-test.yaml#NoSuchAction")

        assertNull(newCache().objectInstance(definition, missing))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertRebuilds(objectLocation: ObjectLocation, editNotation: (GraphNotation) -> GraphNotation) {
        val cache = newCache()
        val baseNotation = AutoTestUtils.readNotation()

        val first = cache.objectInstance(definitionOf(baseNotation), objectLocation)
        val second = cache.objectInstance(definitionOf(editNotation(baseNotation)), objectLocation)

        assertNotSame(first?.reference, second?.reference)
    }


    private fun newCache(): GraphInstanceCache {
        return GraphInstanceCache(GraphCreator, GraphEnvironment.empty)
    }


    private fun definitionOf(graphNotation: GraphNotation): GraphDefinition {
        return AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
    }


    private fun edit(
        notation: GraphNotation,
        location: ObjectLocation,
        attribute: String,
        value: AttributeNotation
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(location, AttributeName(attribute), value))
            .graphNotation
    }


    private fun execute(instance: Any?): ExecutionValue {
        val action = assertIs<DetachedAction>(instance)
        val result = runBlocking {
            action.execute(ExecutionRequest(RequestParams.empty, null))
        }
        return assertIs<ExecutionSuccess>(result).value
    }
}
