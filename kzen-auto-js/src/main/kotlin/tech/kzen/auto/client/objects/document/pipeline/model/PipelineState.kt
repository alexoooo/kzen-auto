package tech.kzen.auto.client.objects.document.pipeline.model

import tech.kzen.auto.client.objects.document.pipeline.input.browse.model.InputBrowserState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedState
import tech.kzen.auto.client.objects.document.pipeline.run.model.PipelineRunState
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


data class PipelineState(
    val mainLocation: ObjectLocation,
    val mainDefinition: ObjectDefinition,
    val input: PipelineInputState = PipelineInputState(),
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


//    fun isRunning(): Boolean {
//        return run.logicStatus?.active != null
//    }


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


    fun withRun(updater: (PipelineRunState) -> PipelineRunState): PipelineState {
        return copy(
            run = updater(run))
    }
}
