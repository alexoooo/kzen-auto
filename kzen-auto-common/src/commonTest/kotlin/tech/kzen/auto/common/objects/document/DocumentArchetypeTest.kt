package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class DocumentArchetypeTest {
    private val parser = YamlNotationParser()

    private val graph = GraphNotation(DocumentPathMap(listOf(
        "types/document.yaml" to """
            CustomDocument:
              abstract: true
        """.trimIndent(),
        "main/Bare.yaml" to """
            main:
              is: CustomDocument
        """.trimIndent(),
        "main/Qualified.yaml" to """
            main:
              is: types/document.yaml#CustomDocument
        """.trimIndent(),
        "main/Unknown.yaml" to """
            main:
              is: MissingDocument
        """.trimIndent()
    ).associate { (path, yaml) ->
        DocumentPath.parse(path) to DocumentNotation(parser.parseDocumentObjects(yaml), null)
    }.toPersistentMap()))

    private val customDocument = ObjectLocation.parse("types/document.yaml#CustomDocument")


    @Test
    fun bareAndQualifiedIsNameTheSameArchetype() {
        for (path in listOf("main/Bare.yaml", "main/Qualified.yaml")) {
            val documentPath = DocumentPath.parse(path)
            assertEquals(ObjectName("CustomDocument"), DocumentArchetype.archetypeName(graph, documentPath))
            assertEquals(customDocument, DocumentArchetype.archetypeLocation(graph, documentPath))
        }
    }


    @Test
    fun anArchetypeTheGraphDoesNotHoldLocatesToNull() {
        assertNull(DocumentArchetype.archetypeLocation(graph, DocumentPath.parse("main/Unknown.yaml")))
    }
}
