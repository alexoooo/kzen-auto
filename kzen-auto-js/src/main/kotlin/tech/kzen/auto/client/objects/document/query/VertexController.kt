package tech.kzen.auto.client.objects.document.query

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import kotlinx.html.title
import org.w3c.dom.HTMLButtonElement
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.query.edge.BottomEgress
import tech.kzen.auto.client.objects.document.query.edge.TopIngress
import tech.kzen.auto.client.objects.document.script.action.AttributeEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.ExecutionIntent
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.query.DataflowWiring
import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.RemoveObjectInAttributeCommand
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import kotlin.js.Date


class VertexController(
        props: Props
):
        RPureComponent<VertexController.Props, VertexController.State>(props),
        ExecutionIntent.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val defaultIcon = "SettingsInputComponent"

        val headerHeight = 55.px

        private val mainIconWidth = 40.px
        private val menuIconOffset = 12.px

        private const val menuDanglingTimeout = 300
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var cellDescriptor: VertexDescriptor,

            var documentPath: DocumentPath,
            var attributeNesting: AttributeNesting,
            var graphStructure: GraphStructure,
            var visualDataflowModel: VisualDataflowModel,
            var dataflowDag: DataflowDag
    ) : RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    private var buttonRef: HTMLButtonElement? = null
//    private var mainDocumentsCache: List<DocumentPath>? = null

    // NB: workaround for open options icon remaining after click with drag away from item
    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


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
            intentToRun = actionLocation == props.cellDescriptor.objectLocation
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(cardOrActions: Boolean) {
        if (state.optionsOpen || processingOption) {
//            console.log("^^^ onMouseOver hoverItem - skip due to optionsOpen")
            return
        }

        optionCompletedTime?.let {
            val now = Date.now()
            val elapsed = now - it
//            console.log("^^^ onMouseOver hoverItem - elapsed", elapsed)

            if (elapsed < menuDanglingTimeout) {
                return
            }
            else {
                optionCompletedTime = null
            }
        }

        if (cardOrActions) {
            setState {
                hoverCard = true
            }
        }
        else {
            setState {
                hoverMenu = true
            }
        }
    }


    private fun onMouseOut(cardOrActions: Boolean) {
        if (cardOrActions) {
            setState {
                hoverCard = false
            }
        }
        else {
            setState {
                hoverMenu = false
            }
        }
    }


    private fun onOptionsOpen() {
        setState {
            optionsOpen = true
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false
            hoverCard = false
            hoverMenu = false
        }
    }


    private fun onOptionsCancel() {
//        console.log("^^^^^^ onOptionsCancel")
        onOptionsClose()
        optionCompletedTime = Date.now()
    }


    private fun performOption(action: suspend () -> Unit) {
        processingOption = true
        onOptionsClose()

        async {
            action.invoke()
        }.then {
            optionCompletedTime = Date.now()
            processingOption = false
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
                    props.documentPath,
                    NotationConventions.mainObjectPath)

            val objectAttributePath = AttributePath(
                    QueryDocument.verticesAttributeName,
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


    private fun visualVertexModel(): VisualVertexModel? {
        return props.visualDataflowModel.vertices[props.cellDescriptor.objectLocation]
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                width = CellController.cardWidth

                height = 100.pct
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(true)
                }

                onMouseOutFunction = {
                    onMouseOut(true)
                }
            }

            renderVertex()
        }
    }


    private fun RBuilder.renderVertex() {
        val cellDescriptor = props.cellDescriptor
        val inputAttributes = DataflowWiring.findInputs(cellDescriptor.objectLocation, props.graphStructure)

        val phase = visualVertexModel()?.phase()

        val cardColor = when (phase) {
            VisualVertexPhase.Pending ->
                Color.white

            VisualVertexPhase.Running ->
                Color.gold

            VisualVertexPhase.Done ->
                Color.white

            VisualVertexPhase.Remaining ->
                Color.white

            VisualVertexPhase.Error ->
                Color.red

            null ->
                Color.gray
        }

        val objectMetadata = props.graphStructure.graphMetadata.get(props.cellDescriptor.objectLocation)!!
        val hasInput = inputAttributes.isNotEmpty()
        val hasOutput = objectMetadata.attributes.values.containsKey(DataflowUtils.mainOutputAttributeName)

        if (hasInput) {
            child(TopIngress::class) {
                attrs {
                    attributeName = inputAttributes[0]
                    ingressColor = cardColor
                }
            }
        }
        else {
            styledDiv {
                css {
                    height = CellController.ingressLength
                }
            }
        }

        if (inputAttributes.size > 1) {
            renderAdditionalInputs(cardColor, inputAttributes)
        }

        renderContent(cardColor, phase)

        if (hasOutput) {
            child(BottomEgress::class) {
                attrs {
                    egressColor = cardColor
                }
            }

            renderVertexEgressMessage()
        }
    }


    private fun RBuilder.renderAdditionalInputs(
            cardColor: Color,
            inputAttributes: List<AttributeName>
    ) {
        styledDiv {
            css {
                backgroundColor = cardColor
                position = Position.absolute

                borderBottomRightRadius = 3.px
                width = CellController.cellWidth.times(inputAttributes.size - 1)
                        .minus(CellController.cellWidth.div(2))
                        .plus(2.em).plus(3.px)
                height = 2.em

                top = CellController.ingressLength
                left = CellController.cardWidth.minus(1.5.em).minus(5.px)
            }
        }

        for (i in 1 until inputAttributes.size) {
            val inputAttribute = inputAttributes[i]
            styledDiv {
                key = inputAttribute.value

                css {
                    position = Position.absolute
                    top = 0.em
                    left = CellController.cellWidth.times(i).minus(1.5.em).minus(2.px)
                }

                child(TopIngress::class) {
                    attrs {
                        attributeName = inputAttribute
                        ingressColor = cardColor
                    }
                }
            }
        }
    }


    private fun RBuilder.renderContent(
            cardColor: Color,
            phase: VisualVertexPhase?
    ) {
        styledDiv {
            css {
                borderRadius = 3.px
                backgroundColor = cardColor
                width = CellController.cardWidth.minus(2.em)
                marginLeft = CellController.cardHorizontalMargin
                marginRight = CellController.cardHorizontalMargin
            }

            renderHeader(phase)
            renderAttributes()
            renderState()
        }
    }


    private fun RBuilder.renderAttributes() {
        val vertexLocation = props.cellDescriptor.objectLocation
        val objectMetadata = props.graphStructure.graphMetadata.objectMetadata[vertexLocation]!!
        val userAttributes = objectMetadata.attributes.values.keys.filterNot {
            it == AutoConventions.iconAttributePath.attribute ||
                    it == AutoConventions.descriptionAttributePath.attribute ||
                    it == CellCoordinate.rowAttributeName ||
                    it == CellCoordinate.columnAttributeName
        }

        val userAttributeValues: Map<AttributeName, AttributeNotation> =
                userAttributes.mapNotNull { attribute ->
                    props.graphStructure.graphNotation.transitiveAttribute(
                            vertexLocation, AttributePath.ofName(attribute)
                    )?.let { notation -> attribute to notation }
                }.toMap()

        if (userAttributeValues.isEmpty()) {
            return
        }

        styledDiv {
            css {
                paddingLeft = 1.em
                paddingRight = 1.em
                paddingBottom = 1.em
            }

            var index = 0
            for ((attribute, notation) in userAttributeValues) {
                styledDiv {
                    key = attribute.value

                    css {
                        if (index++ != 0) {
                            marginTop = 0.5.em
                        }
                    }

                    renderAttribute(attribute, notation)
                }
            }
        }
    }


    private fun RBuilder.renderAttribute(
            attributeName: AttributeName,
            attributeValue: AttributeNotation
    ) {
        when (attributeValue) {
            is ScalarAttributeNotation -> {
                val scalarValue = attributeValue.value

                child(AttributeEditor::class) {
                    attrs {
                        objectLocation = props.cellDescriptor.objectLocation
                        this.attributeName = attributeName
                        value = scalarValue
                    }
                }
            }

            is ListAttributeNotation -> {
                if (attributeValue.values.all { it.asString() != null }) {
                    val stringValues = attributeValue.values.map { it.asString()!! }

                    child(AttributeEditor::class) {
                        attrs {
                            objectLocation = props.cellDescriptor.objectLocation
                            this.attributeName = attributeName
                            values = stringValues
                        }
                    }
                }
                else {
                    +"$attributeName: $attributeValue"
                }
            }

            else ->
                +"$attributeValue"
        }
    }


    private fun RBuilder.renderHeader(
            phase: VisualVertexPhase?
    ) {
        val title = props.graphStructure.graphNotation
                .transitiveAttribute(
                        props.cellDescriptor.objectLocation,
                        AutoConventions.titleAttributePath
                )?.asString()

        val description = props.graphStructure.graphNotation
                .transitiveAttribute(
                        props.cellDescriptor.objectLocation,
                        AutoConventions.descriptionAttributePath
                )?.asString()

        styledDiv {
            css {
                position = Position.relative
                height = headerHeight
                width = 100.pct
//                margin(1.em)
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = mainIconWidth

                    left = (-4).px
                    top = (3).px
                }

                renderIcon(description, phase)
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 100.pct.minus(mainIconWidth).minus(menuIconOffset)
                    top = 0.px
                    left = mainIconWidth
                    marginLeft = 1.em
                }

                renderName(title, description)
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 23.px

//                    top = (-16).px
                    right = 1.5.em
                    top = 0.px
//                    right = 0.px
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

                if (! (state.hoverCard || state.hoverMenu)) {
                    display = Display.none
                }
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(false)
                }

                onMouseOutFunction = {
                    onMouseOut(false)
                }
            }


//            child(MaterialIconButton::class) {
//                attrs {
//                    title = "Remove"
//
//                    onClick = ::onRemove
//                }
//
//                child(DeleteIcon::class) {}
//            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Options..."
                    onClick = ::onOptionsOpen

                    buttonRef = {
                        this@VertexController.buttonRef = it
                    }
                }

                child(MoreVertIcon::class) {}
            }
        }

        child(MaterialMenu::class) {
            attrs {
                open = state.optionsOpen

                onClose = ::onOptionsCancel

                anchorEl = buttonRef
            }

            renderMenuItems()
        }
    }


    private fun RBuilder.renderMenuItems() {
        val iconStyle = reactStyle {
            marginRight = 1.em
        }

        child(MaterialMenuItem::class) {
            attrs {
                onClick = ::onRemove
            }
            child(DeleteIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Delete"
        }
    }


    private fun RBuilder.renderIcon(
            description: String?,
            phase: VisualVertexPhase?
    ) {
        val icon = props.graphStructure.graphNotation
                .transitiveAttribute(props.cellDescriptor.objectLocation, AutoConventions.iconAttributePath)
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
//                        marginTop = (-7).px
                        fontSize = 1.75.em
                        borderRadius = 20.px

                        backgroundColor = highlight
                    }
                }
            }
        }
    }


    private fun RBuilder.renderVertexEgressMessage() {
        val hasNext = visualVertexModel()?.hasNext ?: false
        if (hasNext) {
            styledDiv {
                css {
                    position = Position.absolute

                    width = 1.em
                    height = 1.em

                    bottom = (2.25).em
                    left = CellController.cardWidth.div(2).minus(0.75.em)
                    zIndex = 999
                }
                attrs {
                    title = "Has more messages"
                }

                child(KeyboardArrowDownIcon::class) {}
            }
        }

        val vertexMessage = visualVertexModel()?.message
        if (vertexMessage != null) {
            val successors = props.dataflowDag.successors[props.cellDescriptor.objectLocation]
                    ?: listOf()

            val isMessagePending =
                    successors.isEmpty() ||
                            successors.any {
                                (props.visualDataflowModel.vertices[it]?.epoch ?: -1) == 0
                            }

            if (isMessagePending) {
                styledDiv {
                    css {
                        position = Position.absolute

                        width = 1.em
                        height = 1.em

                        bottom = (1).em
                        left = CellController.cardWidth.div(2).minus(1.5.em)
                        zIndex = 999
                    }

                    child(MaterialIconButton::class) {
                        attrs {
                            title = "Message"

                            style = reactStyle {
                                color = Color.black

                                backgroundColor = Color("rgba(255, 215, 0, 0.175)")
                            }
                        }

                        child(MailIcon::class) {}
                    }
                }

                styledDiv {
                    css {
                        position = Position.absolute

                        width = 0.em
                        height = 1.em

                        bottom = 0.em
                        left = CellController.cardWidth.div(2).plus(2.em)
                    }

                    attrs {
                        title = "Message content"
                    }

                    +"${vertexMessage.get()}"
                }
            }
        }
    }


    private fun RBuilder.renderName(
            title: String?,
            description: String?
    ) {
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

        val name = props.cellDescriptor.objectLocation.objectPath.name
        val displayName =
                if (AutoConventions.isAnonymous(name)) {
                    if (title != null) {
                        title
                    }
                    else {
                        val objectNotation =
                                props.graphStructure.graphNotation.coalesce[props.cellDescriptor.objectLocation]!!
                        val parentReference = ObjectReference.parse(
                                objectNotation.get(NotationConventions.isAttributePath)?.asString()!!)
                        val parentLocation = props.graphStructure.graphNotation.coalesce.locate(parentReference)

                        parentLocation.objectPath.name.value
                    }
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
                        this.title = description
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


    private fun RBuilder.renderState() {
//        console.log("^^^^ renderState", props.visualVertexModel)
        val vertexState = visualVertexModel()?.state
                ?: return

        styledDiv {
            css {
                padding(0.em, 0.5.em, 0.5.em, 0.5.em)
            }

            styledDiv {
                css {
                    backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                    padding(0.5.em)
                }

                +"${vertexState.get()}"
            }
        }
    }
}