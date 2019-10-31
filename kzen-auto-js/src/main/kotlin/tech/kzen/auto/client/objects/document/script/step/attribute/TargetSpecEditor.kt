package tech.kzen.auto.client.objects.document.script.step.attribute

import react.RBuilder
import react.RHandler
import react.RState
import react.ReactElement
import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner
import tech.kzen.auto.common.objects.document.feature.TargetType
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.store.LocalGraphStore


@Suppress("unused")
class TargetSpecEditor(
        props: AttributeEditorProps
):
        RPureComponent<AttributeEditorProps, TargetSpecEditor.State>(props),
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var value: ObjectLocation?,
            var renaming: Boolean
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation
    ) :
            AttributeEditorWrapper(objectLocation) {
        override fun child(input: RBuilder, handler: RHandler<AttributeEditorProps>): ReactElement {
            return input.child(TargetSpecEditor::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val attributeNotation = props
                .graphStructure
                .graphNotation
                .transitiveAttribute(props.objectLocation, props.attributeName)
                as? MapAttributeNotation
                ?: return

        val type= attributeNotation
                .get(TargetSpecDefiner.typeKey)
                ?.asString()
                ?.let { TargetType.valueOf(it) }
                ?: return

        +"[type: $type]"
    }
}