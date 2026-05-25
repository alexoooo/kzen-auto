package tech.kzen.auto.server.objects.script

import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


class ScriptTreeTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun treeOrderByDefinition() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse("test/script-tree-test.yaml")

        val tree = ScriptTree.read(documentPath, graphDefinitionAttempt.successful())

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


    @Test
    fun treeOrderByNotation() {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse("test/script-tree-undefined-test.yaml")

        val tree = ScriptTree.read(documentPath, graphDefinitionAttempt.successful())

        assertEquals(
            listOf(),
            tree.predecessors(
                ObjectPath.parse("main.steps/Formula")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Loop")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Loop.steps/Item")))

        assertEquals(
            listOf(
                ObjectPath.parse("main.steps/Formula"),
                ObjectPath.parse("main.steps/Loop.steps/Item")),
            tree.predecessors(
                ObjectPath.parse("main.steps/Loop.steps/Display")))
    }
}