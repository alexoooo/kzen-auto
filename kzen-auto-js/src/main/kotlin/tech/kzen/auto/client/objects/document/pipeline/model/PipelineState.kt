package tech.kzen.auto.client.objects.document.pipeline.model

import tech.kzen.auto.client.objects.document.pipeline.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedState
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputState
import tech.kzen.auto.client.objects.document.pipeline.run.model.PipelineRunState
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


data class PipelineState(
    val mainLocation: ObjectLocation,
    val mainDefinition: ObjectDefinition,
    val input: PipelineInputState = PipelineInputState(),
    val output: PipelineOutputState = PipelineOutputState(),
    val run: PipelineRunState = PipelineRunState(),
    val notationError: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun tryMainLocation(clientState: SessionState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (! isPipeline(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }


        fun isPipeline(documentNotation: DocumentNotation): Boolean {
            val mainObjectNotation =
                documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

            val mainObjectIs =
                mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

            return mainObjectIs == PipelineConventions.objectName.value
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun inputSpec(): InputSpec {
        val inputDefinition = mainDefinition.attributeDefinitions[PipelineConventions.inputAttributeName]!!
        return (inputDefinition as ValueAttributeDefinition).value as InputSpec
    }


    fun formulaSpec(): FormulaSpec {
        val inputDefinition = mainDefinition.attributeDefinitions[PipelineConventions.formulaAttributeName]!!
        return (inputDefinition as ValueAttributeDefinition).value as FormulaSpec
    }


    fun analysisSpec(): AnalysisSpec {
        val inputDefinition = mainDefinition.attributeDefinitions[PipelineConventions.analysisAttributeName]!!
        return (inputDefinition as ValueAttributeDefinition).value as AnalysisSpec
    }


    fun outputSpec(): OutputSpec {
        val inputDefinition = mainDefinition.attributeDefinitions[PipelineConventions.outputAttributeName]!!
        return (inputDefinition as ValueAttributeDefinition).value as OutputSpec
    }


    fun isRunningOrLoading(): Boolean {
        return run.logicStatus?.active != null
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return null if there is no column listing, otherwise input columns + calculated columns
     */
    fun inputAndCalculatedColumns(): HeaderListing? {
        if (input.column.columnListing == null) {
            return null
        }
        return HeaderListing(input.column.columnListing + formulaSpec().formulas.keys)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withNotationError(notationError: String?): PipelineState {
        return copy(
            notationError = notationError)
    }


    fun withInputBrowser(updater: (InputBrowserState) -> InputBrowserState): PipelineState {
        return copy(
            input = input.copy(
                browser = updater(input.browser)))
    }


    fun withInputSelected(updater: (InputSelectedState) -> InputSelectedState): PipelineState {
        return copy(
            input = input.copy(
                selected = updater(input.selected)))
    }


    fun withOutput(updater: (PipelineOutputState) -> PipelineOutputState): PipelineState {
        return copy(
            output = updater(output))
    }


    fun withRun(updater: (PipelineRunState) -> PipelineRunState): PipelineState {
        return copy(
            run = updater(run))
    }
}
