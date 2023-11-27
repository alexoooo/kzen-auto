package tech.kzen.auto.server.objects.sequence

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator
import kotlin.test.assertEquals


class SequenceTreeTest {
    @Test
    fun singleAscii() {
        val graphNotation = readNotation()
        val documentPath = DocumentPath.parse("test/script-tree-test.yaml")
        val documentNotation = graphNotation.documents[documentPath]!!

        val tree = SequenceTree.read(documentNotation)

        assertEquals(
            listOf(),
            tree.predecessors(
                ObjectPath.parse("main.steps/Formula")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Mapping")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Mapping.steps/Item")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula"),
                ObjectPath.parse("main.steps/Mapping.steps/Item")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Mapping.steps/Is divisible by")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula"),
                ObjectPath.parse("main.steps/Mapping")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Display")))
    }


    private fun readNotation(): GraphNotation {
        val locator = GradleLocator(true)
        val notationMedia = FileNotationMedia(locator)

        val notationParser: NotationParser = YamlNotationParser()

        return runBlocking {
            val notationProjectBuilder =
                mutableMapOf<DocumentPath, DocumentNotation>()

            for (notationPath in notationMedia.scan().documents.values) {
                val notationModule = notationMedia.readDocument(notationPath.key)
                val objects = notationParser.parseDocumentObjects(notationModule)
                notationProjectBuilder[notationPath.key] = DocumentNotation(
                    objects,
                    null)
            }
            GraphNotation(
                DocumentPathMap(
                notationProjectBuilder.toPersistentMap())
            )
        }
    }
}