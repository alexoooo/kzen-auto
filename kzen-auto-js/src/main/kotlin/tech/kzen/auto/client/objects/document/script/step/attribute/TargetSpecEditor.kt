package tech.kzen.auto.client.objects.document.script.step.attribute

import kotlinx.css.em
import kotlinx.css.fontSize
import react.RBuilder
import react.RHandler
import react.RState
import react.ReactElement
import styled.styledDiv
import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.wrap.*
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
            var targetType: TargetType,

            var targetText: String?,

            var targetLocation: ObjectLocation?,
            var renamingTarget: Boolean
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

        val selected= attributeNotation
                .get(TargetSpecDefiner.typeKey)
                ?.asString()
                ?.let { TargetType.valueOf(it) }
                ?: return

        val selectLabelId = "material-react-input-label-id"

        styledDiv {
            child(MaterialFormControl::class) {
                child(MaterialInputLabel::class) {
                    attrs {
                        id = selectLabelId
                    }
                    +"Target"
                }

                child(MaterialSelect::class) {
                    attrs {
                        labelId = selectLabelId

                        style = reactStyle {
                            fontSize = 0.8.em
                        }

                        value = selected.name

                        onChange = {
                            console.log("^^ event", it)
                        }
                    }

                    for (type in TargetType.values()) {
                        child(MaterialMenuItem::class) {
                            key = type.name

                            attrs {
                                value = type.name
                            }

                            +type.name
                        }
                    }
                }
            }
        }

        when (selected) {
            TargetType.Focus ->
                +"Currently focused element"

            TargetType.Text ->
                +"Text"

            TargetType.Xpath ->
                +"XPath"

            TargetType.Visual ->
                +"Visual"
        }
    }
}