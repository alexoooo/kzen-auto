package tech.kzen.auto.client.objects.document.query

import kotlinx.css.*
import kotlinx.css.properties.borderLeft
import kotlinx.css.properties.borderRight
import kotlinx.css.properties.borderTop
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RState
import react.dom.div
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.ExecutionIntent
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
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
    companion object {
        private const val defaultIcon = "SettingsInputComponent"

        val headerHeight = 2.5.em
        private val mainIconWidth = 40.px
        private val menuIconOffset = 12.px

        private val cardWidth = 20.em
    }

    class Props(
            var attributeNesting: AttributeNesting,
            var objectLocation: ObjectLocation,
            var graphStructure: GraphStructure,

            var visualDataflowModel: VisualDataflowModel,
            var dataflowDag: DataflowDag
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean//,

//            var visualVertexModel: VisualVertexModel?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        hoverCard = false
        hoverMenu = false
        intentToRun = false

        optionsOpen = false

//        visualVertexModel = props.visualDataflowModel.vertices[props.objectLocation]
//                ?: VisualVertexModel.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.executionIntent.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.executionIntent.unobserve(this)
    }


//    override fun componentDidUpdate(
//            prevProps: Props,
//            prevState: State,
//            snapshot: Any
//    ) {
////        console.log("ProjectController componentDidUpdate", state, prevState)
//
//        if (props.visualDataflowModel != prevProps.visualDataflowModel) {
//            setState {
//                visualVertexModel = props.visualDataflowModel.vertices[props.objectLocation]
//            }
//        }
//    }

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


    private fun visalVertexModel(): VisualVertexModel? {
        return props.visualDataflowModel.vertices[props.objectLocation]
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledSpan {
            css {
                width = cardWidth
            }

//            attrs {
//                onMouseOverFunction = {
//                    onMouseOver(true)
//                }
//
//                onMouseOutFunction = {
//                    onMouseOut(true)
//                }
//            }

            renderCard()
        }
    }


    private fun RBuilder.renderCard() {
        val objectNotation = props.graphStructure.graphNotation.coalesce[props.objectLocation]!!
        val parentReference = ObjectReference.parse(
                objectNotation.get(NotationConventions.isAttributePath)?.asString()!!)
        val parentLocation = props.graphStructure.graphNotation.coalesce.locate(parentReference)
        val isPipe = parentLocation.objectPath.name.value.endsWith("Pipe")

        val phase = visalVertexModel()?.phase()

        val cardColor = when (phase) {
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
                backgroundColor = cardColor
                borderRadius = 3.px
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                width = 20.em
            }

            if (isPipe) {
                renderPipe()
            }
            else {
                renderFitting(phase)
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


    private fun RBuilder.renderFitting(
            phase: VisualVertexPhase?
    ) {
        val objectMetadata = props.graphStructure.graphMetadata.get(props.objectLocation)!!
        val hasInput = objectMetadata.attributes.values.containsKey(DataflowUtils.inputAttributeName)
        val hasOutput = objectMetadata.attributes.values.containsKey(DataflowUtils.outputAttributeName)

        if (hasInput) {
            renderIngress()
        }

        renderContent(phase)

        if (hasOutput) {
            renderEgress()
        }
    }


    private fun RBuilder.renderContent(
            phase: VisualVertexPhase?
    ) {
        styledDiv {
            css {
                display = Display.block
//                marginTop = 1.5.em
//                marginBottom = 1.em
//                marginLeft = 1.em
//                marginRight = 1.em
                margin(1.em)
//                backgroundColor = cardColor
            }

            styledDiv {
                css {
                    paddingTop = 1.em
                }
                renderHeader(phase)
            }

            renderBody()
        }
    }


    private fun RBuilder.renderBody() {
        predecessorAvailable()
        renderIterations()
        renderState()
        renderMessage()
    }


    private fun RBuilder.renderHeader(
            phase: VisualVertexPhase?
    ) {
        val description = props.graphStructure.graphNotation
                .transitiveAttribute(props.objectLocation, AutoConventions.descriptionAttributePath)
                ?.asString()

        styledDiv {
            css {
                position = Position.relative
                height = headerHeight
                width = 100.pct
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = mainIconWidth
                    top = (-12).px
                    left = (-20).px
                }

                renderIcon(description, phase)
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 100.pct.minus(mainIconWidth).minus(menuIconOffset)
//                    top = (-11).px
                    top = (-13).px
                    left = mainIconWidth
                }

                renderName(description)
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 23.px

//                    top = (-20).px
//                    top = (-15).px
                    top = (-16).px

//                    right = 0.px
                    right = 9.px
                }

                renderOptionsMenu()
            }
        }
    }

    private fun RBuilder.renderOptionsMenu() {
        styledSpan {
            css {
                // NB: blinks in and out without this
                backgroundColor = Color.transparent

//                if (! (state.hoverCard || state.hoverMenu)) {
//                    display = Display.none
//                }
            }

//            attrs {
//                onMouseOverFunction = {
//                    onMouseOver(false)
//                }
//
//                onMouseOutFunction = {
//                    onMouseOut(false)
//                }
//            }


            child(MaterialIconButton::class) {
                attrs {
                    title = "Remove"

                    onClick = ::onRemove
                }

                child(DeleteIcon::class) {}
            }

//            child(MaterialIconButton::class) {
//                attrs {
//                    title = "Options..."
//                    onClick = ::onOptionsOpen
//
//                    buttonRef = {
//                        this@ActionController.buttonRef = it
//                    }
//                }
//
//                child(MoreVertIcon::class) {}
//            }
        }

//        child(MaterialMenu::class) {
//            attrs {
//                open = state.optionsOpen
//
//                onClose = ::onOptionsCancel
//
//                anchorEl = buttonRef
//            }
//
//            renderMenuItems()
//        }
    }


    private fun RBuilder.renderIcon(
            description: String?,
            phase: VisualVertexPhase?
    ) {
        val icon = props.graphStructure.graphNotation
                .transitiveAttribute(props.objectLocation, AutoConventions.iconAttributePath)
                ?.asString()
                ?: defaultIcon

        val highlight =
                if (state.intentToRun && phase != VisualVertexPhase.Running) {
                    Color("rgba(255, 215, 0, 0.5)")
                }
                else {
                    Color("rgba(255, 255, 255, 0.5)")
                }

        child(MaterialIconButton::class) {
            attrs {
                if (description?.isNotEmpty() == true) {
                    attrs {
                        title = description
                    }
                }

                val overfill = 8.px
                style = reactStyle {
                    marginLeft = overfill
                    width = mainIconWidth.plus(overfill)
                    height = mainIconWidth.plus(overfill)

                    backgroundColor = highlight
                }

//                onClick = ::onRun
//                onMouseOver = ::onRunEnter
//                onMouseOut = ::onRunLeave
            }

            child(iconClassForName(icon)) {
                attrs {
                    style = reactStyle {
                        color = Color.black

                        marginTop = (-9).px
                        fontSize = 1.75.em
                        borderRadius = 20.px

                        backgroundColor = highlight
                    }
                }
            }
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


    private fun RBuilder.renderName(description: String?) {
//        child(ActionNameEditor::class) {
//            attrs {
//                objectLocation = props.objectLocation
//                notation = props.graphStructure.graphNotation
//
//                description = actionDescription
//                intentToRun = state.intentToRun
//
//                runCallback = ::onRun
//                editSignal = this@ActionController.editSignal
//            }
//        }

        val name = props.objectLocation.objectPath.name
        val displayName =
                if (AutoConventions.isAnonymous(name)) {
                    val objectNotation = props.graphStructure.graphNotation.coalesce[props.objectLocation]!!
                    val parentReference = ObjectReference.parse(
                            objectNotation.get(NotationConventions.isAttributePath)?.asString()!!)
                    val parentLocation = props.graphStructure.graphNotation.coalesce.locate(parentReference)

                    parentLocation.objectPath.name.value
                }
                else {
                    name.value
                }

        styledDiv {
            css {
                height = headerHeight
                width = 100.pct
            }

            styledDiv {
                css {
                    display = Display.inlineBlock

                    cursor = Cursor.pointer
                    height = headerHeight
                    width = 100.pct

                    marginTop = 10.px
                }

                attrs {
                    if (description != null) {
                        title = description
                    }

//                    onMouseOverFunction = {
//                        onRunEnter()
//                    }
//
//                    onMouseOutFunction = {
//                        onRunLeave()
//                    }
//
//                    onClickFunction = {
//                        onRun()
//                    }
                }

                styledSpan {
                    css {
                        width = 100.pct
                        height = headerHeight

                        fontSize = LinearDimension("1.5em")
                        fontWeight = FontWeight.bold

                        if (state.intentToRun) {
                            classes.add(CssClasses.glowingText)
                        }
                    }

                    +displayName
//                    +"Foo"
                }
            }
        }
    }


    private fun RBuilder.predecessorAvailable() {
//        console.log("^^^^ renderState", props.visualVertexModel)
        val iteration = visalVertexModel()?.iteration
                ?: return

        if (iteration != 0) {
            return
        }

        val predecessors = props.dataflowDag.predecessors[props.objectLocation]
                ?: return

        if (predecessors.isEmpty()) {
            return
        }

        val hasInputsAvailable = predecessors
                .map { props.visualDataflowModel.vertices[it] }
                .any { it?.message != null }

        div {
            if (hasInputsAvailable) {
                +"[Input available]"
            }
            else {
                +"[Input missing]"
            }
        }
    }


    private fun RBuilder.renderIterations() {
//        console.log("^^^^ renderState", props.visualVertexModel)

        val iteration = visalVertexModel()?.iteration
                ?: return

        div {
            +"Iteration: $iteration"
        }
    }


    private fun RBuilder.renderState() {
//        console.log("^^^^ renderState", props.visualVertexModel)

        val vertexState = visalVertexModel()?.state
                ?: return

        div {
            +"State: ${vertexState.get()}"
        }
    }


    private fun RBuilder.renderMessage() {
//        console.log("^^^^ renderState", props.visualVertexModel)

        val vertexMessage = visalVertexModel()?.message
        val hasNext = visalVertexModel()?.hasNext ?: false

        if (vertexMessage == null && ! hasNext) {
            return
        }

        div {
            vertexMessage?.let {
                +"Message: ${it.get()}"
            }

            if (hasNext) {
                +" [Has more messages]"
            }
        }
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