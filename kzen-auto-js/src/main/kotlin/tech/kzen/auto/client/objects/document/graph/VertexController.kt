package tech.kzen.auto.client.objects.document.graph

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.IconButton
import mui.material.Menu
import mui.material.MenuItem
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.graph.edge.BottomEgress
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.CssClasses
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.graph.DataflowWiring
import tech.kzen.auto.common.objects.document.graph.GraphDocument
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualVertexPhase
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
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
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectInAttributeCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import web.dom.Element
import kotlin.js.Date




//---------------------------------------------------------------------------------------------------------------------
external interface VertexControllerProps: Props {
    var attributeController: AttributeController.Wrapper

    var cellDescriptor: VertexDescriptor

    var documentPath: DocumentPath
    var attributeNesting: AttributeNesting

    var clientState: SessionState
    var visualDataflowModel: VisualDataflowModel
    var dataflowMatrix: DataflowMatrix
    var dataflowDag: DataflowDag
}


external interface VertexControllerState: State {
    var hoverCard: Boolean
    var hoverMenu: Boolean
    var intentToRun: Boolean

    var optionsOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class VertexController(
        props: VertexControllerProps
):
        RPureComponent<VertexControllerProps, VertexControllerState>(props),
        ExecutionIntentGlobal.Observer
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
    private var menuAnchorRef: RefObject<Element> = createRef()

    // NB: workaround for open options icon remaining after click with drag away from item
    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun VertexControllerState.init(props: VertexControllerProps) {
        hoverCard = false
        hoverMenu = false
        intentToRun = false

        optionsOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.executionIntentGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.executionIntentGlobal.unobserve(this)
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove() {
        async {
            val sourceMain = ObjectLocation(
                    props.documentPath,
                    NotationConventions.mainObjectPath)

            val objectAttributePath = AttributePath(
                    GraphDocument.verticesAttributeName,
                    props.attributeNesting)

            ClientContext.mirroredGraphStore.apply(RemoveObjectInAttributeCommand(
                    sourceMain, objectAttributePath))
        }
    }


    private fun visualVertexModel(): VisualVertexModel? {
        return props.visualDataflowModel.vertices[props.cellDescriptor.objectLocation]
    }


    private fun hasInputMessage(
            inputName: AttributeName
    ): Boolean {
        val sourceVertex = props.dataflowMatrix.traceVertexBackFrom(props.cellDescriptor, inputName)
                ?: return false

        val sourceVisualModel = props.visualDataflowModel.vertices[sourceVertex.objectLocation]
                ?: return false

        return sourceVisualModel.message != null
    }


    private fun hasMessage(): Boolean {
        return visualVertexModel()?.message != null
    }


    private fun isMessagePending(): Boolean {
        if (visualVertexModel()?.message == null) {
            return false
        }

        val successors = props.dataflowDag.successors[props.cellDescriptor.objectLocation]
                ?: return false

        return successors.isEmpty() ||
                successors.any {
                    (props.visualDataflowModel.vertices[it]?.epoch ?: -1) == 0
                }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)
                width = CellController.cardWidth

                height = 100.pct
            }

            onMouseOver = {
                onMouseOver(true)
            }

            onMouseOut = {
                onMouseOut(true)
            }

            renderVertex()
        }
    }


    private fun ChildrenBuilder.renderVertex() {
        val cellDescriptor = props.cellDescriptor
        val inputAttributes = DataflowWiring.findInputs(
            cellDescriptor.objectLocation, props.clientState.graphStructure())

        val isRunning = props.visualDataflowModel.isRunning()
        val visualVertexModel = visualVertexModel()
        val phase = visualVertexModel?.phase()
        val nextToRun = DataflowUtils.next(
                props.documentPath,
                props.clientState.graphStructure(),
                props.visualDataflowModel)

        val isNextToRun = props.cellDescriptor.objectLocation == nextToRun

        val isSendingMessage =
                isMessagePending() &&
                nextToRun != null &&
                props.cellDescriptor in props.dataflowMatrix.traceVertexBackFrom(nextToRun)

        val cardColor = when {
            phase == VisualVertexPhase.Running ->
                NamedColor.gold

            isNextToRun ->
                EdgeController.goldLight50

            isSendingMessage ->
                EdgeController.goldLight75

            visualVertexModel?.hasNext ?: false ->
                EdgeController.goldLight93

            else -> when (phase) {
                VisualVertexPhase.Pending ->
                    NamedColor.white

                VisualVertexPhase.Done ->
                    NamedColor.white

                VisualVertexPhase.Remaining ->
                    NamedColor.white

                VisualVertexPhase.Error ->
                    NamedColor.red

                null ->
                    NamedColor.gray

                else ->
                    throw IllegalStateException()
            }
        }

        val objectMetadata = props.clientState.graphStructure().graphMetadata.get(props.cellDescriptor.objectLocation)!!
        val hasInput = inputAttributes.isNotEmpty()
        val hasOutput = objectMetadata.attributes.values.containsKey(DataflowUtils.mainOutputAttributeName)

        if (hasInput) {
            renderInput(inputAttributes[0], isRunning, isNextToRun, visualVertexModel)

            if (inputAttributes.size > 1) {
                renderAdditionalInputs(cardColor, inputAttributes, isRunning, isNextToRun, visualVertexModel)
            }
        }
        else {
            div {
                css {
                    height = CellController.ingressLength
                }
            }
        }

        renderContent(cardColor, phase)

        if (hasOutput) {
            val isMessagePending = isMessagePending()

            val egressColor = when {
                isNextToRun ->
                    if ((visualVertexModel?.epoch ?: 0) > 0) {
                        cardColor
                    }
                    else {
                        NamedColor.white
                    }

                isSendingMessage ->
                    if (isRunning) {
                        EdgeController.goldLight25
                    }
                    else {
                        NamedColor.gold
                    }

                phase == VisualVertexPhase.Running ->
                    NamedColor.white

                isMessagePending ->
                    EdgeController.goldLight50

                else ->
                    cardColor
            }

            BottomEgress::class.react {
                this.egressColor = egressColor
            }

            renderVertexEgressMessage(isMessagePending)
        }
    }


    private fun ChildrenBuilder.renderInput(
            inputName: AttributeName,
            isRunning: Boolean,
            isNextToRun: Boolean,
            visualVertexModel: VisualVertexModel?
    ) {
        val ingressColor =
                if ((visualVertexModel?.epoch ?: 0) == 0 &&
                        (isNextToRun || visualVertexModel?.phase() == VisualVertexPhase.Running) &&
                        hasInputMessage(inputName)) {
                    if (isRunning) {
                        EdgeController.goldLight25
                    }
                    else {
                        NamedColor.gold
                    }
                }
                else {
                    NamedColor.white
                }

        TopIngress::class.react {
            attributeName = inputName
            this.ingressColor = ingressColor
        }
    }


    private fun ChildrenBuilder.renderAdditionalInputs(
            cardColor: Color,
            inputAttributes: List<AttributeName>,
            isRunning: Boolean,
            isNextToRun: Boolean,
            visualVertexModel: VisualVertexModel?
    ) {
        div {
            css {
                backgroundColor = cardColor
                position = Position.absolute

                borderBottomRightRadius = 3.px
                width = (inputAttributes.size - 1).times(CellController.cellWidth)
                        .minus(CellController.cellWidth.div(2))
                        .plus(2.em).plus(3.px)
                height = 2.em

                top = CellController.ingressLength
                left = CellController.cardWidth.minus(1.5.em).minus(7.px)

                zIndex = integer(-99)
            }
        }

        for (i in 1 until inputAttributes.size) {
            val inputAttribute = inputAttributes[i]
            div {
                key = inputAttribute.value

                css {
                    position = Position.absolute
                    top = 0.em
                    left = i.times(CellController.cellWidth).minus(1.5.em).minus(4.px)
                }

                renderInput(inputAttribute, isRunning, isNextToRun, visualVertexModel)
            }
        }
    }


    private fun ChildrenBuilder.renderContent(
            cardColor: Color,
            phase: VisualVertexPhase?
    ) {
        div {
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


    private fun ChildrenBuilder.renderAttributes() {
        val vertexLocation = props.cellDescriptor.objectLocation

        val objectMetadata = props
            .clientState.graphStructure().graphMetadata.objectMetadata[vertexLocation]!!

        val editableAttributes = objectMetadata.attributes.values.keys.filterNot {
            AutoConventions.isManaged(it) ||
                    it == CellCoordinate.rowAttributeName ||
                    it == CellCoordinate.columnAttributeName ||
                    props.clientState.graphStructure().graphNotation.firstAttribute(
                            vertexLocation, AttributePath.ofName(it)
                    ) == null
        }
//        val userAttributeValues: Map<AttributeName, AttributeNotation> =
//                editableAttributes.mapNotNull { attribute ->
//                    props.graphStructure.graphNotation.transitiveAttribute(
//                            vertexLocation, AttributePath.ofName(attribute)
//                    )?.let { notation -> attribute to notation }
//                }.toMap()

        if (editableAttributes.isEmpty()) {
            return
        }

        div {
            css {
                paddingLeft = 1.em
                paddingRight = 1.em
                paddingBottom = 1.em
            }

            var index = 0
            for (attributeName in editableAttributes) {
                div {
                    key = attributeName.value

                    css {
                        if (index++ != 0) {
                            marginTop = 0.5.em
                        }
                    }

                    renderAttribute(attributeName)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderAttribute(
            attributeName: AttributeName
    ) {
        props.attributeController.child(this) {
            this.clientState = props.clientState
            this.objectLocation = props.cellDescriptor.objectLocation
            this.attributeName = attributeName
        }
    }


    private fun ChildrenBuilder.renderHeader(
            phase: VisualVertexPhase?
    ) {
        val title = props.clientState.graphStructure().graphNotation
                .firstAttribute(
                        props.cellDescriptor.objectLocation,
                        AutoConventions.titleAttributePath
                )?.asString()

        val description = props.clientState.graphStructure().graphNotation
                .firstAttribute(
                        props.cellDescriptor.objectLocation,
                        AutoConventions.descriptionAttributePath
                )?.asString()

        div {
            css {
                position = Position.relative
                height = headerHeight
                width = 100.pct
            }

            div {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = mainIconWidth

                    left = (-4).px
                    top = (3).px
                }

                renderIcon(description, phase)
            }

            div {
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

            div {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 23.px
                    right = 1.5.em
                    top = 0.px
                }

                ref = this@VertexController.menuAnchorRef

                renderOptionsMenu()
            }
        }
    }


    private fun ChildrenBuilder.renderOptionsMenu() {
        span {
            css {
                // NB: blinks in and out without this
                backgroundColor = NamedColor.transparent

                if (! (state.hoverCard || state.hoverMenu)) {
                    display = None.none
                }
            }

            onMouseOver = {
                onMouseOver(false)
            }

            onMouseOut = {
                onMouseOut(false)
            }

            IconButton {
                title = "Options..."
                onClick = {
                    onOptionsOpen()
                }
                MoreVertIcon::class.react {}
            }
        }

        Menu {
            open = state.optionsOpen
            onClose = ::onOptionsCancel
            anchorEl = {
                menuAnchorRef.current!!
            }

            renderMenuItems()
        }
    }


    private fun ChildrenBuilder.renderMenuItems() {
        MenuItem {
            onClick = {
                onRemove()
            }

            DeleteIcon::class.react {
                style = jso {
                    marginRight = 1.em
                }
            }

            +"Delete"
        }
    }


    private fun ChildrenBuilder.renderIcon(
            description: String?,
            phase: VisualVertexPhase?
    ) {
        val icon = props.clientState.graphStructure().graphNotation
                .firstAttribute(props.cellDescriptor.objectLocation, AutoConventions.iconAttributePath)
                ?.asString()
                ?: defaultIcon

        val highlight =
                if (state.intentToRun && phase != VisualVertexPhase.Running) {
                    Color("rgba(255, 215, 0, 0.5)")
                }
                else {
                    Color("rgba(255, 255, 255, 0.5)")
                }

        IconButton {
            if (description?.isNotEmpty() == true) {
                title = description
            }

            val overfill = 8.px
            css {
                marginLeft = overfill
                width = mainIconWidth.plus(overfill)
                height = mainIconWidth.plus(overfill)

                backgroundColor = highlight
                position = Position.relative
            }

            iconClassForName(icon).react {
                style = jso {
                    color = NamedColor.black

                    fontSize = 1.75.em
                    borderRadius = 20.px

                    backgroundColor = highlight

                    margin = Margin(0.em, 0.em, 0.em, 0.em)
                    padding = Padding(0.em, 0.em, 0.em, 0.em)

                    position = Position.absolute
                    top = 3.px
                    left = 3.px
                }
            }
        }
    }


    private fun ChildrenBuilder.renderVertexEgressMessage(
            isMessagePending: Boolean
    ) {
        val hasNext = visualVertexModel()?.hasNext ?: false
        if (hasNext) {
            div {
                css {
                    position = Position.absolute

                    width = 1.em
                    height = 1.em

                    bottom = (2.25).em
                    left = CellController.cardWidth.div(2).minus(0.75.em)
                    zIndex = integer(999)
                }
                title = "Has more messages"

                KeyboardArrowDownIcon::class.react {}
            }
        }

        if (hasMessage()) {
            val vertexMessage = visualVertexModel()?.message!!

            div {
                css {
                    position = Position.absolute

                    width = 1.em
                    height = 1.em

                    bottom = (1).em
                    left = CellController.cardWidth.div(2).minus(1.5.em)
                    zIndex = integer(999)
                }

                IconButton {
                    title = "Message"

                    css {
                        color = NamedColor.black

                        if (isMessagePending) {
                            backgroundColor = Color("rgba(255, 215, 0, 0.175)")
                        }
                    }

                    MailIcon::class.react {}
                }
            }

            div {
                css {
                    position = Position.absolute

                    width = 0.em
                    height = 1.em

                    bottom = 0.em
                    left = CellController.cardWidth.div(2).plus(2.em)
                }

                title = "Message content"

                +"${vertexMessage.get()}"
            }
        }
    }


    private fun ChildrenBuilder.renderName(
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
                        val objectNotation = props.clientState.graphStructure()
                            .graphNotation.coalesce[props.cellDescriptor.objectLocation]!!

                        val parentReference = ObjectReference.parse(
                                objectNotation.get(NotationConventions.isAttributePath)?.asString()!!)

                        val parentLocation = props.clientState.graphStructure()
                            .graphNotation.coalesce.locate(parentReference)

                        parentLocation.objectPath.name.value
                    }
                }
                else {
                    name.value
                }

        div {
            css {
                height = headerHeight
                width = 100.pct
            }

            div {
                css {
                    display = Display.inlineBlock

                    cursor = Cursor.pointer
                    height = headerHeight
                    width = 100.pct

                    marginTop = 10.px
                }

                if (description != null) {
                    this.title = description
                }

                span {
                    if (state.intentToRun) {
                        className = ClassName(CssClasses.glowingText)
                    }

                    css {
                        width = 100.pct
                        height = headerHeight

                        fontSize = 1.5.em
                        fontWeight = FontWeight.bold
                    }

                    +displayName
                }
            }
        }
    }


    private fun ChildrenBuilder.renderState() {
//        console.log("^^^^ renderState", props.visualVertexModel)
        val vertexState = visualVertexModel()?.state
                ?: return

        div {
            css {
                padding = Padding(0.em, 0.5.em, 0.5.em, 0.5.em)
            }

            div {
                css {
                    backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                    padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
                }

                +"${vertexState.get()}"
            }
        }
    }
}