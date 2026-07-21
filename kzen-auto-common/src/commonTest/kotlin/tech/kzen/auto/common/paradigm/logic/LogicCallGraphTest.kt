package tech.kzen.auto.common.paradigm.logic

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


/**
 * Traversal semantics of [LogicCallGraph], over a self-contained notation fixture: no archetype from any
 * particular paradigm appears here, which is the point — the edge is defined by attribute METADATA
 * (`is: ObjectLocation`) plus [tech.kzen.auto.common.util.AutoConventions.isLogic], never by a step type.
 * That the real `RunStep.instructions` declaration satisfies it is pinned separately, server-side, by
 * `LinkedLogicDocumentsTest`.
 */
class LogicCallGraphTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val alpha = DocumentPath.parse("alpha.yaml")
    private val beta = DocumentPath.parse("beta.yaml")
    private val gamma = DocumentPath.parse("gamma.yaml")
    private val plain = DocumentPath.parse("plain.yaml")
    private val toPlain = DocumentPath.parse("to-plain.yaml")
    private val unresolved = DocumentPath.parse("unresolved.yaml")
    private val cycleA = DocumentPath.parse("cycle-a.yaml")
    private val cycleB = DocumentPath.parse("cycle-b.yaml")

    private val documents = mapOf(
        // The archetypes: a `Logic` marker for isLogic to find in an inheritance chain, an `ObjectLocation`
        // type object for attribute metadata to point at, and a hosting archetype declaring a link attribute.
        DocumentPath.parse("base.yaml") to """
Logic:
  abstract: true

Plain:
  abstract: true

ObjectLocation:
  abstract: true
  class: ${ObjectLocation.className.get()}

Caller:
  abstract: true
  instructions: ""
  meta:
    instructions:
      is: ObjectLocation
""",

        // alpha -> beta -> gamma, plus an intra-document link that must NOT register as an edge
        alpha to """
main:
  is: Logic

Call:
  is: Caller
  instructions: "beta.yaml#main"

Local:
  is: Caller
  instructions: "alpha.yaml#Call"
""",

        beta to """
main:
  is: Logic

Call:
  is: Caller
  instructions: "gamma.yaml#main"
""",

        gamma to """
main:
  is: Logic
""",

        plain to """
main:
  is: Plain
""",

        toPlain to """
main:
  is: Logic

Call:
  is: Caller
  instructions: "plain.yaml#main"
""",

        unresolved to """
main:
  is: Logic

Dangling:
  is: Caller
  instructions: "missing.yaml#main"

Blank:
  is: Caller
""",

        cycleA to """
main:
  is: Logic

Call:
  is: Caller
  instructions: "cycle-b.yaml#main"
""",

        cycleB to """
main:
  is: Logic

Call:
  is: Caller
  instructions: "cycle-a.yaml#main"
""")


    private val graphStructure: GraphStructure by lazy {
        val yamlParser = YamlNotationParser()

        val graphNotation = GraphNotation(DocumentPathMap(
            documents
                .mapValues { DocumentNotation(yamlParser.parseDocumentObjects(it.value), null) }
                .toPersistentMap()))

        GraphStructure(graphNotation, NotationMetadataReader().read(graphNotation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun calleesFollowTheChainRecursively() {
        assertEquals(
            setOf(beta, gamma),
            LogicCallGraph.transitiveCallees(graphStructure, alpha))
    }


    @Test
    fun callersAreTheInverseOfCallees() {
        assertEquals(
            setOf(beta, alpha),
            LogicCallGraph.transitiveCallers(graphStructure, gamma))
    }


    @Test
    fun theSeedDocumentIsExcludedFromBothDirections() {
        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallees(graphStructure, gamma))

        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallers(graphStructure, alpha))
    }


    @Test
    fun intraDocumentLinkIsNotAnEdge() {
        // alpha's `Local` points at another object in alpha itself
        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallers(graphStructure, alpha))
    }


    @Test
    fun linkToNonLogicDocumentIsNotAnEdge() {
        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallees(graphStructure, toPlain))

        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallers(graphStructure, plain))
    }


    @Test
    fun blankAndDanglingLinksAreSkipped() {
        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallees(graphStructure, unresolved))
    }


    @Test
    fun mutualHostingCycleTerminatesAndReachesTheSeed() {
        assertEquals(
            setOf(cycleB, cycleA),
            LogicCallGraph.transitiveCallees(graphStructure, cycleA))

        assertEquals(
            setOf(cycleB, cycleA),
            LogicCallGraph.transitiveCallers(graphStructure, cycleA))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The two traversals walk the same edges in opposite directions, so they must never disagree - the
    // suggestion filter would otherwise drop (or keep) a document the migration signal treats differently.
    @Test
    fun calleesAndCallersAgree() {
        for (callee in documents.keys) {
            val callers = LogicCallGraph.transitiveCallers(graphStructure, callee)

            for (caller in documents.keys) {
                assertEquals(
                    caller in callers,
                    callee in LogicCallGraph.transitiveCallees(graphStructure, caller),
                    "$caller -> $callee")
            }
        }
    }
}
