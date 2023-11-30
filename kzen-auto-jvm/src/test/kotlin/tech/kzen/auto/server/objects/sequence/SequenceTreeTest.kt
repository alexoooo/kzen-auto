package tech.kzen.auto.server.objects.sequence

import org.junit.Test
import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


class SequenceTreeTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun treeOrder() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse("test/script-tree-test.yaml")

        val tree = SequenceTree.read(documentPath, graphDefinitionAttempt.successful())

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

}