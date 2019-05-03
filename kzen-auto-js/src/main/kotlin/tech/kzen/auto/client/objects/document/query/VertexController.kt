package tech.kzen.auto.client.objects.document.query

import kotlinx.css.*
import kotlinx.css.properties.borderLeft
import kotlinx.css.properties.borderRight
import kotlinx.css.properties.borderTop
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import styled.styledH3
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.ExecutionIntent
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.RemoveObjectInAttributeCommand


class VertexController(
        props: Props
):
        RPureComponent<VertexController.Props, VertexController.State>(props),
        ExecutionIntent.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeNesting: AttributeNesting,
            var objectLocation: ObjectLocation,
            var graphStructure: GraphStructure,

            var visualVertexModel: VisualVertexModel?
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        hoverCard = false
        hoverMenu = false
        intentToRun = false

        optionsOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.executionIntent.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.executionIntent.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onExecutionIntent(actionLocation: ObjectLocation?) {
        setState {
            intentToRun = actionLocation == props.objectLocation
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun editParameterCommandAsync() {
//        async {
//            editParameterCommand()
//        }
//    }


//    private suspend fun editParameterCommand() {
//        ClientContext.commandBus.apply(UpsertAttributeCommand(
//                props.objectLocation,
//                filePathAttribute.attribute,
//                ScalarAttributeNotation(state.value)))
//
//        setState {
//            pending = false
//        }
//
//        executeAction()
//    }


//    private suspend fun executeAction() {
//        val executionResult = ClientContext.restClient.performDetached(props.objectLocation)
//        setState {
//            this.executionResult = executionResult
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove() {
        async {
            val sourceMain = ObjectLocation(
                    props.objectLocation.documentPath,
                    NotationConventions.mainObjectPath)

            val objectAttributePath = AttributePath(
                    QueryDocument.verticesAttributePath.attribute,
                    props.attributeNesting)

            ClientContext.commandBus.apply(RemoveObjectInAttributeCommand(
                    sourceMain, objectAttributePath))
        }
    }


//    private fun onValueChange(newValue: String) {
//        setState {
//            value = newValue
//            pending = true
//        }
//
//        state.submitDebounce.apply()
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        +"state.intentToRun: ${state.intentToRun}"

        styledDiv {
            css {
                backgroundColor = Color.white
                borderRadius = 3.px
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
            }

            val objectNotation = props.graphStructure.graphNotation.coalesce.get(props.objectLocation)!!
            val parentReference = ObjectReference.parse(
                    objectNotation.get(NotationConventions.isAttributePath)?.asString()!!)
            val parentLocation = props.graphStructure.graphNotation.coalesce.locate(parentReference)

            if (parentLocation.objectPath.name.value.endsWith("Pipe")) {
                renderPipe()
            }
            else {
                renderFitting()
            }
        }
    }

    private fun RBuilder.renderPipe() {
        renderIngress()

        styledDiv {
            css {
                display = Display.block
                marginTop = 1.5.em
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Remove"

                    style = reactStyle {
                        float = Float.right
                        marginTop = (-1).em
                    }

                    onClick = ::onRemove
                }

                child(DeleteIcon::class) {}
            }
        }

        renderEgress()
    }


    private fun RBuilder.renderFitting() {
        val objectMetadata = props.graphStructure.graphMetadata.get(props.objectLocation)!!
        val hasInput = objectMetadata.attributes.values.containsKey(DataflowUtils.inputAttributeName)
        val hasOutput = objectMetadata.attributes.values.containsKey(DataflowUtils.outputAttributeName)

        if (hasInput) {
            renderIngress()
        }

        renderContent()

        if (hasOutput) {
            renderEgress()
        }
    }


    private fun RBuilder.renderContent() {
        val cardColor = when (props.visualVertexModel?.phase()) {
            VisualVertexPhase.Pending ->
//                    Color("#649fff")
                Color.white

            VisualVertexPhase.Running ->
                Color.gold

            VisualVertexPhase.Done ->
//                    Color("#00a457")
                Color("#00b467")
//                    Color("#13aa59")
//                    Color("#1faf61")

            VisualVertexPhase.Remaining ->
                Color("#00a457")

            VisualVertexPhase.Error ->
                Color.red

            null ->
                Color.gray
        }

        styledDiv {
            css {
                display = Display.block
////                marginTop = 1.5.em
//                marginBottom = 1.em
//                marginLeft = 1.em
//                marginRight = 1.em
                margin(1.em)

                backgroundColor = cardColor
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Remove"

                    style = reactStyle {
                        float = Float.right
                    }

                    onClick = ::onRemove
                }

                child(DeleteIcon::class) {}
            }

            styledH3 {
                css {
                    width = 10.em

                    if (state.intentToRun) {
                        classes.add(CssClasses.glowingText)
                    }
                }

                child(SettingsInputComponentIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = (0.5).em
                        }
                    }
                }

                renderName()
            }

            renderState()
        }
    }


    private fun RBuilder.renderIngress() {
        styledDiv {
            css {
                position = Position.absolute

                width = 10.px
                height = 0.px

                borderTop(10.px, BorderStyle.solid, Color.white)
                borderLeft(5.px, BorderStyle.solid, Color.transparent)
                borderRight(5.px, BorderStyle.solid, Color.transparent)

                top = (-19).px
                left = (100).px
//                zIndex = -999
            }
        }

        styledDiv {
            css {
                backgroundColor = Color.white
                position = Position.absolute

                width = 10.px
                height = 10.px

                top = (-10).px
                left = (105).px
            }
        }
    }


    private fun RBuilder.renderEgress() {
        styledDiv {
            css {
                backgroundColor = Color.white
                position = Position.absolute

                width = 10.px
                height = 10.px

                bottom = (-10).px
                left = (105).px
            }
        }

        styledDiv {
            css {
                position = Position.absolute

                width = 0.px
                height = 0.px

//                borderBottom(10.px, BorderStyle.solid, Color.white)
//                borderLeft(5.px, BorderStyle.solid, Color.transparent)
//                borderRight(5.px, BorderStyle.solid, Color.transparent)

                borderTop(10.px, BorderStyle.solid, Color.white)
                borderLeft(10.px, BorderStyle.solid, Color.transparent)
                borderRight(10.px, BorderStyle.solid, Color.transparent)

//                bottom = (-19).px
                bottom = (-19).px
                left = (100).px
//                zIndex = 999
            }
        }
    }


    private fun RBuilder.renderName() {
        val name = props.objectLocation.objectPath.name

        if (AutoConventions.isAnonymous(name)) {
            val objectNotation = props.graphStructure.graphNotation.coalesce.get(props.objectLocation)!!
            val parentReference = ObjectReference.parse(
                    objectNotation.get(NotationConventions.isAttributePath)?.asString()!!)
            val parentLocation = props.graphStructure.graphNotation.coalesce.locate(parentReference)

            +"${parentLocation.objectPath.name}"
        }
        else {
            +name.value
        }
    }


    private fun RBuilder.renderState() {
//        console.log("^^^^ renderState", props.visualVertexModel)

        val vertexState = props.visualVertexModel?.state
                ?: return

        +"State: ${vertexState.get()}"
    }


//    private fun RBuilder.renderResult(executionResult: ExecutionResult) {
//        styledDiv {
//            css {
//                marginTop = 1.em
//            }
//
//            when (executionResult) {
//                is ExecutionError -> {
//                    styledDiv {
//                        css {
//                            color = Color.red
//                        }
//                        +executionResult.errorMessage
//                    }
//                }
//
//                is ExecutionSuccess -> {
//                    +executionResult.value.get().toString()
//                }
//            }
//        }
//    }
}