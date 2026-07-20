package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.store.LocalGraphStore
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * The repository's structure-digest fast path must actually engage (it was declared but never assigned), while
 * still retrying whenever a plugin failed to make it into the definer cache — the only path by which a jar that
 * appears on disk WITHOUT a notation change is ever picked up. Observed through the internal refresh counter,
 * because the fast path still calls [LocalGraphStore.graphStructure] (it needs the digest) and the deep path's
 * only collaborators are the kzen-lib singletons, which cannot be mocked.
 */
class PluginReportDefinitionRepositoryTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val pluginDocument = DocumentPath.parse("test/plugin-cache-test.yaml")
    private val someCoordinate = PluginCoordinate("no-such-definer")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun secondCallWithUnchangedNotationSkipsRefresh() {
        val store = FakeGraphStore(structureOf(notationWithoutPlugins()))
        val repository = repository(store)

        repository.listMetadata()
        repository.listMetadata()

        assertEquals(1, repository.refreshCount, "second call against unchanged notation must hit the fast path")
    }


    @Test
    fun notationEditInvalidates() {
        val baseNotation = notationWithoutPlugins()
        val store = FakeGraphStore(structureOf(baseNotation))
        val repository = repository(store)

        repository.listMetadata()

        store.graphStructure = structureOf(edit(
            baseNotation,
            ObjectLocation(
                DocumentPath.parse("test/script-engine-if-test.yaml"),
                ObjectPath.parse("main.steps/Flag")),
            "code",
            ScalarAttributeNotation("false")))
        repository.listMetadata()

        assertEquals(2, repository.refreshCount, "a notation edit must invalidate the cached digest")
    }


    @Test
    fun incompleteDefinerCacheKeepsRetrying() {
        // The fixture's jar path doesn't exist, so jarClassLoader() is null and the plugin never enters the
        // definer cache. Freezing the digest here would lock the plugin out until the next notation edit.
        val store = FakeGraphStore(structureOf(AutoTestUtils.readNotation()))
        val repository = repository(store)

        repository.contains(someCoordinate)
        repository.contains(someCoordinate)

        assertEquals(2, repository.refreshCount, "an incomplete definer cache must keep retrying")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun repository(graphStore: LocalGraphStore): PluginReportDefinitionRepository {
        return PluginReportDefinitionRepository(graphStore, GraphDefiner, GraphCreator)
    }


    private fun notationWithoutPlugins(): GraphNotation {
        return AutoTestUtils.readNotation().withoutDocument(pluginDocument)
    }


    private fun structureOf(graphNotation: GraphNotation): GraphStructure {
        return GraphStructure(graphNotation, AutoTestUtils.graphMetadata(graphNotation))
    }


    private fun edit(
        notation: GraphNotation,
        location: ObjectLocation,
        attribute: String,
        value: ScalarAttributeNotation
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(location, AttributeName(attribute), value))
            .graphNotation
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class FakeGraphStore(
        var graphStructure: GraphStructure
    ): LocalGraphStore {
        override suspend fun observe(observer: LocalGraphStore.Observer) {}

        override fun unobserve(observer: LocalGraphStore.Observer) {}

        override suspend fun graphNotation(): GraphNotation =
            graphStructure.graphNotation

        override suspend fun graphStructure(): GraphStructure =
            graphStructure

        override suspend fun graphDefinition(): GraphDefinitionAttempt =
            error("Not used by the metadata paths under test")
    }
}
