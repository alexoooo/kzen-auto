package tech.kzen.auto.server.exec.flow

import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * A Flow vertex's transitive definition closure contains no sibling vertices: channels are
 * define-time values ([tech.kzen.auto.common.objects.document.flow.FlowWiring] allocates them as
 * ValueAttributeDefinitions), edges are grid geometry rather than references, and RunLogicVertex's
 * `instructions` link is weak by design (see [tech.kzen.auto.server.service.impl.LinkedLogicDocuments]), so
 * the callee is compiled separately. Each vertex is therefore instantiable on its own.
 *
 * [FlowRun] builds one graph per run rather than one per vertex, so nothing in production depends on
 * that today - this test keeps the option structurally open, and prints the closure-vs-document
 * definition counts that say how much per-vertex scoping would actually save.
 */
class FlowVertexClosureTest {
    @Test
    fun vertexClosuresExcludeSiblingsAndAreCreatable() {
        // this document hosts a RunLogicVertex, the one vertex kind with a (weak) link to another document
        val documentPath = DocumentPath.parse("test/flow-run-test.yaml")

        val notation = AutoTestUtils.readNotation()
        val definition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful

        val vertexLocations = FlowMatrix
            .ofDocument(documentPath, definition.graphStructure)
            .verticesByLocation
            .keys
        assertTrue(vertexLocations.size > 1, "expected multiple vertices: $vertexLocations")

        val documentScopedSize = definition.filterTransitive(documentPath).objectDefinitions.map.size

        for (vertexLocation in vertexLocations) {
            val scoped = definition.filterTransitive(vertexLocation)
            val closure = scoped.objectDefinitions.map.keys

            val siblings = vertexLocations - vertexLocation
            assertTrue(closure.none { it in siblings },
                "$vertexLocation closure must not pull sibling vertices: $closure")
            assertTrue(closure.size < documentScopedSize,
                "$vertexLocation closure ${closure.size} not smaller than document $documentScopedSize")

            // standalone create succeeds - a RunLogicVertex builds without its (weak) child document,
            // and no vertex needs a @Service, so the empty environment suffices
            val created = GraphCreator.createGraph(scoped, GraphEnvironment.empty)
            assertNotNull(created[vertexLocation])

            println("G3a measurement: $vertexLocation closure=${closure.size} document=$documentScopedSize")
        }
    }
}
