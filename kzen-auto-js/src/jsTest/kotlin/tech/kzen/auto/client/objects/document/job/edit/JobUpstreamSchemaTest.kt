package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
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
import kotlin.test.assertNull


class JobUpstreamSchemaTest {
    private val provider = ObjectLocation.parse("job.yaml#main.workers/provider")
    private val sink = ObjectLocation.parse("job.yaml#main.workers/sink")


    @Test
    fun liveSummaryPrecedesValidated() {
        val live = tabular("live")
        val validated = JobUpstreamSchema.ContractResult.Available(tabular("validated"))

        assertEquals(
            JobUpstreamSchema.Result(
                JobUpstreamSchema.Provider.LiveSummary,
                JobUpstreamSchema.ContractResult.Available(live)),
            JobUpstreamSchema.choose(live, validated))
        assertEquals(
            JobUpstreamSchema.Result(JobUpstreamSchema.Provider.Validated, validated),
            JobUpstreamSchema.choose(null, validated))
        assertNull(JobUpstreamSchema.choose(null, JobUpstreamSchema.ContractResult.Unavailable))
    }


    @Test
    fun upstreamWorkersValidatedOutputSuppliesColumns() {
        val result = JobUpstreamSchema.columns(
            graph(), sink, emptyMap(), validation(StepValidation(null, null, contract = tabular("id", "amount"))))

        assertEquals(JobUpstreamSchema.Provider.Validated, result?.provider)
        assertEquals(listOf("id", "amount"), result?.columns?.values?.map { it.text })
    }


    @Test
    fun dynamicOrMissingValidationSuppliesNothing() {
        assertNull(JobUpstreamSchema.columns(graph(), sink, emptyMap(), null))
        assertNull(JobUpstreamSchema.columns(
            graph(), sink, emptyMap(), validation(StepValidation(null, null, contract = DataContract(DataType.Dynamic())))))
        assertNull(JobUpstreamSchema.columns(
            graph(), sink, emptyMap(), validation(StepValidation(null, null, contract = null))))
    }


    @Test
    fun upstreamValidationErrorIsReported() {
        val result = JobUpstreamSchema.columns(
            graph(), sink, emptyMap(), validation(StepValidation(null, "Unable to read value 1 before Run: gone")))

        assertEquals(
            JobUpstreamSchema.ContractResult.Error("Unable to read value 1 before Run: gone"),
            result?.result)
    }


    private fun validation(step: StepValidation): JobValidation =
        JobValidation(mapOf(provider.objectPath to step))


    private fun graph(): GraphStructure {
        val parser = YamlNotationParser()
        val types = """
            Job:
              abstract: true
            ChannelOutput:
              abstract: true
              class: tech.kzen.auto.common.paradigm.job.api.ChannelOutput
            ChannelInput:
              abstract: true
              class: tech.kzen.auto.common.paradigm.job.api.ChannelInput
            Worker:
              abstract: true
            ProviderWorker:
              abstract: true
              is: Worker
              output: ""
              meta:
                output:
                  is: ChannelOutput
            SinkWorker:
              abstract: true
              is: Worker
              input: ""
              meta:
                input:
                  is: ChannelInput
        """.trimIndent()
        val job = """
            main:
              is: Job
            main.workers/provider:
              is: types.yaml#ProviderWorker
            main.workers/sink:
              is: types.yaml#SinkWorker
        """.trimIndent()
        val documents = mapOf(
            DocumentPath.parse("types.yaml") to
                DocumentNotation(parser.parseDocumentObjects(types), null),
            DocumentPath.parse("job.yaml") to
                DocumentNotation(parser.parseDocumentObjects(job), null))
        val notation = GraphNotation(DocumentPathMap(documents.toPersistentMap()))
        return GraphStructure(notation, NotationMetadataReader().read(notation))
    }


    private fun tabular(vararg columns: String): DataContract =
        LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(columns.toList())).itemType
}
