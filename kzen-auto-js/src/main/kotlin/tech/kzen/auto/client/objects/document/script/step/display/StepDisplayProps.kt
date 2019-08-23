package tech.kzen.auto.client.objects.document.script.step.display

import react.RProps
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


open class StepDisplayProps(
        var graphStructure: GraphStructure,
        var objectLocation: ObjectLocation,
        var attributeNesting: AttributeNesting,
        var imperativeModel: ImperativeModel
//        var imperativeState: ImperativeState?
): RProps