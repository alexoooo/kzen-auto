package tech.kzen.auto.client.objects.document.common.attribute

import react.Props
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface AttributeEditorProps: Props {
    var objectLocation: ObjectLocation
    var attributeName: AttributeName

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore

    // Optional (null on editors that don't wire it): the run cluster's edit-pending channel, passed to an
    // AttributeCommitter so keystrokes light up the "revalidating" indicator immediately. Only editors whose
    // Wrapper injects it set it — currently KotlinExpressionEditor (see the scope note in the run-cluster plan).
    var logicValidationGlobal: LogicValidationGlobal?
}