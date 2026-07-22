package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * [JobSignatureCapability] derives a Job's Logic signature — inputs from the `parameters` branch of typed
 * ParameterBinding declarations, outputs from the document's declared `results` signature map (Script parity) —
 * the single source shared by [tech.kzen.auto.server.exec.job.JobLogicCompiler] (server) and the
 * callee-parameter editors (client). Loads a real notation graph and asserts:
 * [JobSignatureCapability.isResultSink] classifies by inheritance chain (a user-defined ResultSink SUBTYPE is
 * recognized with no code change, CC-17); [JobSignatureCapability.signature] yields the parameter names in
 * document order, types each parameter from its declaration (untyped -> Any, generics preserved), yields the
 * declared result components in declaration order with their declared types; and a non-Job document yields the
 * empty signature. Reads only notation — nothing is instantiated.
 */
class JobSignatureCapabilityTest {
    companion object {
        private val documentPath = DocumentPath.parse("test/job-signature-capability-test.yaml")
        private val jobMainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        private val graphStructure: GraphStructure by lazy {
            val graphNotation = AutoTestUtils.readNotation()
            GraphStructure(graphNotation, AutoTestUtils.graphMetadata(graphNotation))
        }
    }


    private fun isResultSink(workerName: String): Boolean {
        val workerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/$workerName"))
        return JobSignatureCapability.isResultSink(graphStructure.graphNotation, workerLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun resultSinkClassifiesAsResult() {
        assertTrue(isResultSink("result"))
    }


    @Test
    fun plainWorkerIsNotAResultSink() {
        assertFalse(isResultSink("plain"))
    }


    @Test
    fun thirdPartyResultSinkSubtypeIsRecognizedWithoutCodeChange() {
        // The CC-17 proof: a user-defined ResultSink subtype classifies purely via its inheritance chain.
        assertTrue(isResultSink("subtypeResult"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun inputParametersAreDeclaredInDocumentOrder() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        assertEquals(listOf("items", "records", "counts"), signature.inputs.components.map { it.name.value })
    }


    @Test
    fun outputComponentsComeFromDeclaredResultsInOrder() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        // The declared `results` map's components, in declaration order — independent of the sink Workers.
        assertEquals(listOf("main", "summary", "typed"), signature.outputs.components.map { it.name.value })
    }


    @Test
    fun undeclaredParameterTypeYieldsAny() {
        // The `type` attribute resolves through the inheritance chain to the ParameterBinding archetype default.
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        val items = signature.inputs.components.first { it.name.value == "items" }
        assertEquals("kotlin.Any", items.type.metadata.className.asString())
    }


    @Test
    fun declaredParameterTypeCarriesIntoSignature() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        val records = signature.inputs.components.first { it.name.value == "records" }
        assertEquals("kotlin.String", records.type.metadata.className.asString())
    }


    @Test
    fun genericParameterTypeIsPreserved() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        val counts = signature.inputs.components.first { it.name.value == "counts" }
        assertEquals("kotlin.collections.List", counts.type.metadata.className.asString())
        assertEquals(
            listOf("kotlin.Int"),
            counts.type.metadata.generics.map { it.className.asString() })
    }


    @Test
    fun declaredResultTypeCarriesIntoSignature() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        val typed = signature.outputs.components.first { it.name.value == "typed" }
        assertEquals("kotlin.String", typed.type.metadata.className.asString())
    }


    @Test
    fun nonJobDocumentYieldsEmptySignature() {
        val scriptLocation = ObjectLocation(
            DocumentPath.parse("test/job-signature-script-test.yaml"), ObjectPath.parse("main"))
        val signature = JobSignatureCapability.signature(graphStructure, scriptLocation)
        assertEquals(0, signature.inputs.components.size)
        assertEquals(0, signature.outputs.components.size)
    }
}
