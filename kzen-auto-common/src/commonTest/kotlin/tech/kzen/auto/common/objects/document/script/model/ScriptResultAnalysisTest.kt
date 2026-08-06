package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * [ScriptResultAnalysis] over a self-contained notation fixture. The archetypes are stand-ins declared in the
 * fixture itself rather than the real `auto-jvm/script/script-jvm.yaml` ones, which is enough because the
 * analysis reads notation alone: Result membership resolves through the `is:` inheritance chain, so what binds
 * is an object NAMED ResultStep anywhere in the graph, not its class or its paradigm wiring.
 */
class ScriptResultAnalysisTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val archetypes = DocumentPath.parse("archetypes.yaml")
    private val implicitTail = DocumentPath.parse("implicit-tail.yaml")
    private val resultLast = DocumentPath.parse("result-last.yaml")
    private val resultFirst = DocumentPath.parse("result-first.yaml")
    private val nestedResult = DocumentPath.parse("nested-result.yaml")
    private val resultSubtype = DocumentPath.parse("result-subtype.yaml")
    private val voidScript = DocumentPath.parse("void.yaml")
    private val noSteps = DocumentPath.parse("no-steps.yaml")


    private val mainWithIntResult = """
main:
  is: Script
  results:
    main:
      class: kotlin.Int
      generics: []
      nullable: false
"""


    private val documents = mapOf(
        archetypes to """
Script:
  abstract: true
  results: {}

ScriptStep:
  abstract: true

FormulaStep:
  abstract: true
  is: ScriptStep

ResultStep:
  abstract: true
  is: ScriptStep

CustomResultStep:
  abstract: true
  is: ResultStep

IfStep:
  abstract: true
  is: ScriptStep

IfBranch:
  abstract: true
""",

        implicitTail to mainWithIntResult + """
main.steps/Base:
  is: FormulaStep

main.steps/Doubled:
  is: FormulaStep
""",

        resultLast to mainWithIntResult + """
main.steps/Base:
  is: FormulaStep

main.steps/Result:
  is: ResultStep
""",

        resultFirst to mainWithIntResult + """
main.steps/Result:
  is: ResultStep

main.steps/Tail:
  is: FormulaStep

main.steps/Tail 2:
  is: FormulaStep
""",

        nestedResult to mainWithIntResult + """
main.steps/Branch:
  is: IfStep

main.steps/Branch.branches/Branch:
  is: IfBranch

main.steps/Branch.branches/Branch.steps/Early:
  is: ResultStep

main.steps/Tail:
  is: FormulaStep
""",

        resultSubtype to mainWithIntResult + """
main.steps/Base:
  is: FormulaStep

main.steps/Custom:
  is: CustomResultStep

main.steps/Tail:
  is: FormulaStep
""",

        voidScript to """
main:
  is: Script

main.steps/Only:
  is: FormulaStep
""",

        noSteps to mainWithIntResult)


    private val graphNotation: GraphNotation by lazy {
        val yamlParser = YamlNotationParser()

        GraphNotation(DocumentPathMap(
            documents
                .mapValues { DocumentNotation(yamlParser.parseDocumentObjects(it.value), null) }
                .toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun analyze(documentPath: DocumentPath): ScriptResultAnalysis {
        return ScriptResultAnalysis.analyze(graphNotation, documentPath)
    }


    private fun rootStep(documentPath: DocumentPath, name: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse("main.steps/$name"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun lastRootStepBecomesTheImplicitResult() {
        val analysis = analyze(implicitTail)

        assertEquals(
            listOf(rootStep(implicitTail, "Base"), rootStep(implicitTail, "Doubled")),
            analysis.rootSteps)

        assertEquals(listOf(), analysis.unreachableRootSteps)
        assertEquals(rootStep(implicitTail, "Doubled"), analysis.implicitResultStep)
        assertTrue(analysis.declaresMainResult)
    }


    @Test
    fun trailingRootResultStepSuppliesTheResultItself() {
        val analysis = analyze(resultLast)

        assertEquals(
            listOf(rootStep(resultLast, "Base"), rootStep(resultLast, "Result")),
            analysis.rootSteps)

        assertEquals(listOf(), analysis.unreachableRootSteps)
        assertNull(analysis.implicitResultStep)
        assertTrue(analysis.declaresMainResult)
    }


    @Test
    fun everyRootStepAfterARootResultStepIsUnreachable() {
        val analysis = analyze(resultFirst)

        assertEquals(
            listOf(rootStep(resultFirst, "Tail"), rootStep(resultFirst, "Tail 2")),
            analysis.unreachableRootSteps)

        assertNull(analysis.implicitResultStep)
    }


    @Test
    fun aResultStepSubtypeEndsTheScriptToo() {
        val analysis = analyze(resultSubtype)

        assertEquals(
            listOf(rootStep(resultSubtype, "Tail")),
            analysis.unreachableRootSteps)

        assertNull(analysis.implicitResultStep)
    }


    @Test
    fun nestedResultStepDoesNotExemptTheRootTail() {
        val analysis = analyze(nestedResult)

        assertEquals(
            listOf(rootStep(nestedResult, "Branch"), rootStep(nestedResult, "Tail")),
            analysis.rootSteps)

        assertEquals(listOf(), analysis.unreachableRootSteps)
        assertEquals(rootStep(nestedResult, "Tail"), analysis.implicitResultStep)
    }


    @Test
    fun aVoidScriptHasNoImplicitResult() {
        val analysis = analyze(voidScript)

        assertEquals(listOf(rootStep(voidScript, "Only")), analysis.rootSteps)
        assertEquals(false, analysis.declaresMainResult)
        assertNull(analysis.implicitResultStep)
    }


    @Test
    fun aScriptWithoutStepsHasNoImplicitResult() {
        val analysis = analyze(noSteps)

        assertEquals(listOf(), analysis.rootSteps)
        assertEquals(listOf(), analysis.unreachableRootSteps)
        assertNull(analysis.implicitResultStep)
        assertTrue(analysis.declaresMainResult)
    }
}
