package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.client.objects.document.job.source.DataSourceResolveStore
import tech.kzen.auto.client.objects.document.job.source.DataSourceShapeStore
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class JobUpstreamSchemaTest {
    private val source = ObjectLocation.parse("job.yaml#main.sources/source")
    private val provider = ObjectLocation.parse("job.yaml#main.workers/provider")
    private val sink = ObjectLocation.parse("job.yaml#main.workers/sink")
    private val unrelated = ObjectLocation.parse("job.yaml#unrelated")


    @Test
    fun liveSummaryPrecedesInspectedSource() {
        val live = HeaderListing.ofUnique(listOf("live"))
        val inspected = HeaderListing.ofUnique(listOf("inspected"))

        assertEquals(
            JobUpstreamSchema.Result(JobUpstreamSchema.Provider.LiveSummary, live),
            JobUpstreamSchema.choose(live, inspected))
    }


    @Test
    fun inspectedSourceIsFallbackBeforeNoProvider() {
        val inspected = HeaderListing.ofUnique(listOf("inspected"))

        assertEquals(
            JobUpstreamSchema.Result(JobUpstreamSchema.Provider.InspectedSource, inspected),
            JobUpstreamSchema.choose(null, inspected))
        assertNull(JobUpstreamSchema.choose(null, null))
    }


    @Test
    fun fullColumnsPathUsesInheritedReadDefaults() {
        val item = part("item")
        val manifest = DataManifest(listOf(DataUnit.of(item)))
        val result = columns(
            graph("ReadWorker"),
            manifest,
            mapOf(item to settled(tabular("default-column"))))

        assertEquals(JobUpstreamSchema.Provider.InspectedSource, result?.provider)
        assertEquals(listOf("default-column"), result?.columns?.values?.map { it.text })
    }


    @Test
    fun fullColumnsPathIgnoresUnselectedInspectingAndFailedParts() {
        val main = part("main", "main")
        val inspecting = part("reference-inspecting", "reference")
        val failed = part("reference-failed", "reference")
        val manifest = DataManifest(listOf(DataUnit.of(main, inspecting, failed)))
        val result = columns(
            graph("ReadMainWorker"),
            manifest,
            linkedMapOf(
                main to settled(tabular("main-column")),
                inspecting to DataSourceShapeStore.PartState(true, null, null),
                failed to DataSourceShapeStore.PartState(false, null, "inspection failed")))

        assertEquals(JobUpstreamSchema.Provider.InspectedSource, result?.provider)
        assertEquals(listOf("main-column"), result?.columns?.values?.map { it.text })
    }


    @Test
    fun graphProjectionHonorsRoleAttributesAndSupersetOrder() {
        val mainA = part("main-a", "main")
        val ignored = part("reference-a", "reference")
        val mainB = part("main-b", "main")
        val manifest = DataManifest(listOf(
            DataUnit(linkedMapOf("date" to "2026"), listOf(mainA, ignored)),
            DataUnit(linkedMapOf("group" to "west"), listOf(mainB))))
        val shapes = mapOf(
            mainA to tabular("a", "shared"),
            ignored to tabular("must-not-appear"),
            mainB to tabular("shared", "b"))

        val projected = JobUpstreamSchema.projectInspectedLane(
            graph("ReadMainColumnsWorker"), provider, manifest, shapes)

        assertEquals(
            listOf("date", "group", "a", "shared", "b"),
            projected?.values?.map { it.text })
    }


    @Test
    fun attributeColumnCollisionFailsClosed() {
        val item = part("item")
        val manifest = DataManifest(listOf(
            DataUnit(linkedMapOf("column" to "attribute"), listOf(item))))

        assertNull(JobUpstreamSchema.projectInspectedLane(
            graph("ReadColumnsWorker"),
            provider,
            manifest,
            mapOf(item to tabular("column"))))
    }


    @Test
    fun blankRoleRequiresOneRolePerUnitAndExplicitRoleExcludesOthers() {
        val main = part("main", "main")
        val reference = part("reference", "reference")
        val manifest = DataManifest(listOf(DataUnit.of(main, reference)))
        val shapes = mapOf(main to tabular("main-column"), reference to tabular("reference-column"))

        assertNull(JobUpstreamSchema.projectInspectedLane(
            graph("ReadWorker"), provider, manifest, shapes))
        assertEquals(
            listOf("main-column"),
            JobUpstreamSchema.projectInspectedLane(
                graph("ReadMainWorker"), provider, manifest, shapes)?.values?.map { it.text })
    }


    @Test
    fun strictMismatchAndUnitsModeDoNotAdvertiseColumns() {
        val first = part("first")
        val second = part("second")
        val manifest = DataManifest(listOf(DataUnit.of(first), DataUnit.of(second)))
        val shapes = mapOf(first to tabular("a"), second to tabular("b"))

        assertNull(JobUpstreamSchema.projectInspectedLane(
            graph("ReadStrictWorker"), provider, manifest, shapes))
        assertNull(JobUpstreamSchema.projectInspectedLane(
            graph("ReadUnitsWorker"), provider, manifest, shapes))
    }


    @Test
    fun capabilityAndConventionalFieldsFailClosed() {
        val item = part("item")
        val manifest = DataManifest(listOf(DataUnit.of(item)))
        val shapes = mapOf(item to tabular("column"))
        val structure = graph("ReadWorker")

        assertNull(JobUpstreamSchema.projectInspectedLane(
            structure, unrelated, manifest, shapes))
        assertNull(JobUpstreamSchema.readProjectionConfig(
            structure, ObjectLocation.parse("job.yaml#incomplete")))
    }


    private fun columns(
        structure: GraphStructure,
        manifest: DataManifest,
        parts: Map<DataPart, DataSourceShapeStore.PartState>
    ): JobUpstreamSchema.Result? {
        val rest = ClientRestApi("unused")
        val resolveStore = DataSourceResolveStore(rest, mapOf(
            source to DataSourceResolveStore.State(
                false, DataResolveResult(manifest, emptyList()), null)))
        val key = DataSourceShapeStore.Key.of(source, manifest)
        val shapeStore = DataSourceShapeStore(rest, mapOf(
            key to DataSourceShapeStore.State(parts, null)))
        return JobUpstreamSchema.columns(
            structure, sink, emptyMap(), resolveStore, shapeStore)
    }


    private fun graph(providerArchetype: String): GraphStructure {
        val parser = YamlNotationParser()
        val types = """
            Job:
              abstract: true
            DataSource:
              abstract: true
              class: tech.kzen.auto.common.data.api.DataSource
            DataSourceShapeProvider:
              abstract: true
            ChannelOutput:
              abstract: true
              class: tech.kzen.auto.common.paradigm.job.api.ChannelOutput
            ChannelInput:
              abstract: true
              class: tech.kzen.auto.common.paradigm.job.api.ChannelInput
            Worker:
              abstract: true
            ReadWorker:
              abstract: true
              is:
                - Worker
                - DataSourceShapeProvider
              output: ""
              source: ""
              emit: items
              role: ""
              attributes: ignore
              schemaMode: superset
              meta:
                output:
                  is: ChannelOutput
                source:
                  is: DataSource
                  nullable: true
            ReadMainWorker:
              abstract: true
              is: ReadWorker
              role: main
            ReadColumnsWorker:
              abstract: true
              is: ReadWorker
              attributes: columns
            ReadMainColumnsWorker:
              abstract: true
              is: ReadMainWorker
              attributes: columns
            ReadStrictWorker:
              abstract: true
              is: ReadWorker
              schemaMode: strict
            ReadUnitsWorker:
              abstract: true
              is: ReadWorker
              emit: units
            SinkWorker:
              abstract: true
              is: Worker
              input: ""
              meta:
                input:
                  is: ChannelInput
            UnrelatedWorker:
              abstract: true
              source: ""
              emit: items
              role: ""
              attributes: ignore
              schemaMode: superset
              meta:
                source:
                  is: DataSource
                  nullable: true
            IncompleteProvider:
              abstract: true
              is: DataSourceShapeProvider
              source: ""
              meta:
                source:
                  is: DataSource
                  nullable: true
            FileDataSource:
              is: DataSource
        """.trimIndent()
        val job = """
            main:
              is: Job
            main.sources/source:
              is: types.yaml#FileDataSource
            main.workers/provider:
              is: types.yaml#$providerArchetype
              source: main.sources/source
            main.workers/sink:
              is: types.yaml#SinkWorker
            unrelated:
              is: types.yaml#UnrelatedWorker
              source: main.sources/source
            incomplete:
              is: types.yaml#IncompleteProvider
              source: main.sources/source
        """.trimIndent()
        val documents = mapOf(
            DocumentPath.parse("types.yaml") to
                DocumentNotation(parser.parseDocumentObjects(types), null),
            DocumentPath.parse("job.yaml") to
                DocumentNotation(parser.parseDocumentObjects(job), null))
        val notation = GraphNotation(DocumentPathMap(documents.toPersistentMap()))
        return GraphStructure(notation, NotationMetadataReader().read(notation))
    }


    private fun settled(shape: DataShape): DataSourceShapeStore.PartState {
        return DataSourceShapeStore.PartState(false, shape, null)
    }


    private fun part(id: String, role: String = "main"): DataPart {
        return DataPart(DataRole(role), DataRef(null, id), null, null)
    }


    private fun tabular(vararg columns: String): DataShape {
        return LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(columns.toList()))
    }
}
