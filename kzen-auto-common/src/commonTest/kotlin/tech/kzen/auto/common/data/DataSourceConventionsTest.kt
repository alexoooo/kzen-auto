package tech.kzen.auto.common.data

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class DataSourceConventionsTest {
    private val parser = YamlNotationParser()

    private fun graph(vararg documents: Pair<String, String>): GraphNotation {
        return GraphNotation(DocumentPathMap(documents.associate { (path, yaml) ->
            DocumentPath.parse(path) to DocumentNotation(parser.parseDocumentObjects(yaml), null)
        }.toPersistentMap()))
    }


    @Test
    fun capabilityRecognitionExcludesArchetypeAndUnrelatedObjects() {
        val graph = graph("types.yaml" to """
            DataSource:
              abstract: true
            FileDataSource:
              is: DataSource
            Derived:
              is: FileDataSource
            Worker: {}
        """.trimIndent())

        assertFalse(DataSourceConventions.isDataSource(graph, ObjectLocation.parse("types.yaml#DataSource")))
        assertTrue(DataSourceConventions.isDataSource(graph, ObjectLocation.parse("types.yaml#FileDataSource")))
        assertTrue(DataSourceConventions.isDataSource(graph, ObjectLocation.parse("types.yaml#Derived")))
        assertFalse(DataSourceConventions.isDataSource(graph, ObjectLocation.parse("types.yaml#Worker")))
        assertFalse(DataSourceConventions.isDataSource(graph, ObjectLocation.parse("types.yaml#Missing")))
    }


    @Test
    fun discoveryIsGraphWideAcrossNestedJobBranches() {
        val graph = graph(
            "types.yaml" to "DataSource:\n  abstract: true\nFileDataSource:\n  abstract: true\n  is: DataSource\n",
            "jobs/one.yaml" to "main.sources/first:\n  is: types.yaml#FileDataSource\n",
            "jobs/two.yaml" to "main.sources/second:\n  is: types.yaml#FileDataSource\n")

        assertEquals(
            listOf(
                ObjectLocation.parse("jobs/one.yaml#main.sources/first"),
                ObjectLocation.parse("jobs/two.yaml#main.sources/second")),
            DataSourceConventions.allDataSources(graph))
    }


    @Test
    fun dataSourceAttributeTypeRecognitionIsExact() {
        assertTrue(DataSourceConventions.isDataSourceType(TypeMetadata(
            ClassName("tech.kzen.auto.common.data.api.DataSource"), emptyList(), true)))
        assertFalse(DataSourceConventions.isDataSourceType(TypeMetadata.string))
        assertFalse(DataSourceConventions.isDataSourceType(null))
    }


    @Test
    fun shapeProjectionRequiresExplicitCapability() {
        val graph = graph("types.yaml" to """
            DataSourceShapeProvider:
              abstract: true
            ReadWorker:
              is: DataSourceShapeProvider
              source: input
            UnrelatedWorker:
              source: input
        """.trimIndent())

        assertTrue(DataSourceConventions.isShapeProvider(
            graph, ObjectLocation.parse("types.yaml#ReadWorker")))
        assertFalse(DataSourceConventions.isShapeProvider(
            graph, ObjectLocation.parse("types.yaml#UnrelatedWorker")))
    }
}
