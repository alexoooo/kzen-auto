package tech.kzen.auto.client.objects.document.script.step.display

import react.RProps
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


open class StepDisplayProps(
        var common: Common
): RProps {
    data class Common(
            var graphStructure: GraphStructure,
            var objectLocation: ObjectLocation,
            var attributeNesting: AttributeNesting,
            var imperativeModel: ImperativeModel,

            var managed: Boolean = false,
            var first: Boolean = false,
            var last: Boolean = false
    ) {
        fun isRunning(): Boolean {
            return objectLocation == imperativeModel.running
        }
    }
}