package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * [JobSignatureCapability] derives a Job's Logic signature from its ParameterSource / ResultSink marker Workers,
 * the single source shared by [tech.kzen.auto.server.exec.job.JobLogicCompiler] (server) and the callee-parameter
 * editors (client). Loads a real notation graph and asserts: [JobSignatureCapability.roleOf] classifies Parameter /
 * Result / null; a user-defined ParameterSource SUBTYPE classifies Parameter with no code change (CC-17);
 * [JobSignatureCapability.signature] yields the parameter / result names in document order, filters a blank
 * parameter, maps a blank result to `main`, keeps a named result, types a port from its `of:` (else Any); and a
 * non-Job document yields the empty signature. Reads only notation + metadata — nothing is instantiated.
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


    private fun roleOf(workerName: String): JobSignatureCapability.Role? {
        val workerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/$workerName"))
        return JobSignatureCapability.roleOf(graphStructure.graphNotation, workerLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parameterSourceClassifiesAsParameter() {
        assertEquals(JobSignatureCapability.Role.Parameter, roleOf("param"))
    }


    @Test
    fun resultSinkClassifiesAsResult() {
        assertEquals(JobSignatureCapability.Role.Result, roleOf("result"))
    }


    @Test
    fun plainWorkerHasNoRole() {
        assertNull(roleOf("plain"))
    }


    @Test
    fun thirdPartyParameterSourceSubtypeIsRecognizedWithoutCodeChange() {
        // The CC-17 proof: a user-defined ParameterSource subtype classifies purely via its inheritance chain.
        assertEquals(JobSignatureCapability.Role.Parameter, roleOf("typedParam"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun inputParametersAreNamedInDocumentOrderWithBlankFiltered() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        // param(items), blankParam(filtered), typedParam(records) -> [items, records] in document order.
        assertEquals(listOf("items", "records"), signature.inputs.components.map { it.name.value })
    }


    @Test
    fun outputComponentsMapBlankToMainAndKeepNamed() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        // result(blank -> main), namedResult(summary) -> [main, summary] in document order.
        assertEquals(listOf("main", "summary"), signature.outputs.components.map { it.name.value })
    }


    @Test
    fun untypedParameterPortYieldsAny() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        val items = signature.inputs.components.first { it.name.value == "items" }
        assertEquals("kotlin.Any", items.type.metadata.className.asString())
    }


    @Test
    fun typedParameterPortYieldsItsElementType() {
        val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
        val records = signature.inputs.components.first { it.name.value == "records" }
        assertEquals(
            "tech.kzen.auto.server.objects.job.worker.DataRecord",
            records.type.metadata.className.asString())
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
