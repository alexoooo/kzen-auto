package tech.kzen.auto.client.objects.document.query

import kotlinx.css.*
import kotlinx.css.properties.borderBottom
import kotlinx.css.properties.borderLeft
import kotlinx.css.properties.borderRight
import kotlinx.css.properties.borderTop
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
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
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
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
import tech.kzen.lib.common.structure.notation.edit.RemoveInAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.RemoveObjectInAttributeCommand
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


class CellController(
        props: Props
):
        RPureComponent<CellController.Props, CellController.State>(props),
        ExecutionIntent.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val defaultIcon = "SettingsInputComponent"

        val headerHeight = 2.5.em
        private val mainIconWidth = 40.px
        private val menuIconOffset = 12.px

        private val cardWidth = 20.em
        private val cellWidth = cardWidth.plus(2.em)
    }


    class Props(
            var cellDescriptor: CellDescriptor,

            var documentPath: DocumentPath,
            var attributeNesting: AttributeNesting,
            var graphStructure: GraphStructure,
            var visualDataflowModel: VisualDataflowModel,
            var dataflowMatrix: DataflowMatrix,
            var dataflowDag: DataflowDag
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean//,

//            var visualVertexModel: VisualVertexModel?
    ): RState


    private fun Props.vertexLocation() =
            (cellDescriptor as? VertexDescriptor)?.objectLocation


    private fun Props.edgeOrientation() =
            (cellDescriptor as? EdgeDescriptor)?.orientation


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


    //-----------------------------------------------------------------------------------------------------------------
    override fun onExecutionIntent(actionLocation: ObjectLocation?) {
        setState {
            intentToRun = actionLocation == props.vertexLocation()
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

            if (isVertex()) {
                val objectAttributePath = AttributePath(
                        QueryDocument.verticesAttributeName,
                        props.attributeNesting)

                ClientContext.commandBus.apply(RemoveObjectInAttributeCommand(
                        sourceMain, objectAttributePath))
            }
            else {
                val objectAttributePath = AttributePath(
                        QueryDocument.edgesAttributeName,
                        props.attributeNesting)

                ClientContext.commandBus.apply(RemoveInAttributeCommand(
                        sourceMain, objectAttributePath))
            }
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


    private fun isVertex(): Boolean {
        return props.vertexLocation() != null
    }


    private fun visualVertexModel(): VisualVertexModel? {
        return props.visualDataflowModel.vertices[props.vertexLocation()]
    }


    private fun isEdgePredecessorOfNextToRun(): Boolean {
        val flowTarget = props.visualDataflowModel.running()
                ?: DataflowUtils.next(
                        props.documentPath,
                        props.graphStructure,
                        props.visualDataflowModel)
                ?: return false

        @Suppress("MapGetWithNotNullAssertionOperator")
        val targetVertexDescriptor = props.dataflowMatrix.verticesByLocation[flowTarget]!!

        for ((i, inputName) in targetVertexDescriptor.inputNames.withIndex()) {
            val sourceVertex = props.dataflowMatrix.traceVertexBackFrom(targetVertexDescriptor, inputName)
                    ?: continue

            val sourceVisualModel = props.visualDataflowModel.vertices[sourceVertex.objectLocation]
                    ?: continue

            if (sourceVisualModel.message == null) {
                continue
            }

            val leadingEdges = props.dataflowMatrix.traceEdgeBackFrom(targetVertexDescriptor, i)
            if (leadingEdges.contains(props.cellDescriptor)) {
                return true
            }
        }

        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (isVertex()) {
            val cellDescriptor = props.cellDescriptor as VertexDescriptor
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
                    if (isVertex()) {
                        Color.gray
                    }
                    else {
                        Color.white
                    }
            }

            styledDiv {
                css {
                    backgroundColor = cardColor
                    borderRadius = 3.px
                    position = Position.relative
                    filter = "drop-shadow(0 1px 1px gray)"
                    width = cardWidth
                }

                renderVertex(phase, cardColor, inputAttributes)
            }
        }
        else {
            styledDiv {
                css {
                    position = Position.relative
                    filter = "drop-shadow(0 1px 1px gray)"
                    width = cardWidth
                }

                renderEdge()
            }
        }
    }


    private fun RBuilder.renderEdge() {
        val orientation = props.edgeOrientation()
                ?: return

        val edgeColor =
                if (isEdgePredecessorOfNextToRun()) {
                    Color.gold
                }
                else {
                    Color.white
                }

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

        if (orientation.hasTop()) {
            renderIngress(DataflowUtils.mainInputAttributeName, edgeColor)
        }

        styledDiv {
            css {
                backgroundColor = edgeColor

                width = 2.em
                height = 2.em
                marginLeft = cardWidth.div(2).minus(1.em)

                if (orientation.hasBottom()) {
                    marginBottom = 2.em
                }
            }
        }

        if (orientation.hasLeftIngress()) {
            renderIngressLeft(edgeColor)
        }

        if (orientation.hasRightEgress()) {
            renderEgressRight(edgeColor)
        }

        if (orientation.hasBottom()) {
            renderEgress(edgeColor)
        }
    }


    private fun RBuilder.renderVertex(
            phase: VisualVertexPhase?,
            cardColor: Color,
            inputAttributes: List<AttributeName>
    ) {
        val objectMetadata = props.graphStructure.graphMetadata.get(props.vertexLocation()!!)!!
        val hasInput = inputAttributes.isNotEmpty()
        val hasOutput = objectMetadata.attributes.values.containsKey(DataflowUtils.mainOutputAttributeName)

        if (hasInput) {
            renderIngress(inputAttributes[0], cardColor)
        }

        if (inputAttributes.size > 1) {
            renderAdditionalInputs(cardColor, inputAttributes)
        }

        renderContent(phase, hasOutput)

        if (hasOutput) {
            renderEgress(cardColor)
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
                width = cellWidth.times(inputAttributes.size - 1)
                        .minus(cellWidth.div(2))
                        .plus(2.em).plus(3.px)
                height = 2.em

                top = 0.em
                left = cardWidth.minus(3.px)
            }
        }

        for (i in 1 until inputAttributes.size) {
            val inputAttribute = inputAttributes[i]
            renderIngress(inputAttribute, cardColor, cellWidth.times(i))
        }
    }


    private fun RBuilder.renderContent(
            phase: VisualVertexPhase?,
            hasOutput: Boolean
    ) {
        styledDiv {
            css {
                display = Display.block
                margin(1.em)

                if (hasOutput) {
                    marginBottom = 2.em
                }
            }

            styledDiv {
                css {
                    paddingTop = 1.em
                }
                renderHeader(phase)
            }

            styledDiv {
                css {
                    paddingBottom = 0.5.em
                }
                renderBody()
            }
        }
    }


    private fun RBuilder.renderBody() {
        renderAttributes()

//        renderPredecessorAvailable()
//        renderIterations()
        renderState()
//        renderMessage()
    }


    private fun RBuilder.renderAttributes() {
        val objectMetadata = props.graphStructure.graphMetadata.objectMetadata[props.vertexLocation()!!]!!

        for (e in objectMetadata.attributes.values) {
            if (e.key == AutoConventions.iconAttributePath.attribute ||
                    e.key == AutoConventions.descriptionAttributePath.attribute ||
                    e.key == CellCoordinate.rowAttributeName ||
                    e.key == CellCoordinate.columnAttributeName) {
                continue
            }

            val keyAttributePath = AttributePath.ofName(e.key)

            val value = props.graphStructure.graphNotation.transitiveAttribute(
                    props.vertexLocation()!!, keyAttributePath
            ) ?: continue

            styledDiv {
                css {
                    marginBottom = 0.5.em
                }

                renderAttribute(e.key,value)
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
                        objectLocation = props.vertexLocation()!!
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
                            objectLocation = props.vertexLocation()!!
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
        val description = props.graphStructure.graphNotation
                .transitiveAttribute(props.vertexLocation()!!, AutoConventions.descriptionAttributePath)
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
                .transitiveAttribute(props.vertexLocation()!!, AutoConventions.iconAttributePath)
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


    private fun RBuilder.renderIngress(
            attributeName: AttributeName,
            cardColor: Color,
            leftOffset: LinearDimension = 0.px
    ) {
        styledDiv {
            css {
                position = Position.absolute

                width = 0.px
                height = 0.px

                borderTop(2.em, BorderStyle.solid, cardColor)
                borderLeft(2.em, BorderStyle.solid, Color.transparent)
                borderRight(2.em, BorderStyle.solid, Color.transparent)

                top = (-2).em
                left = cardWidth.div(2).minus(2.em).plus(leftOffset)
            }
        }

        styledDiv {
            css {
                backgroundColor = cardColor
                position = Position.absolute

                width = 2.em
                height = 2.em

                top = (-2).em
                left = cardWidth.div(2).minus(1.em).plus(leftOffset)
            }
        }

        if (attributeName != DataflowUtils.mainInputAttributeName) {
            styledDiv {
                css {
                    position = Position.absolute

//                    width = 2.em
                    height = 1.em

                    top = (-1.25).em
                    right = cardWidth.div(2).plus(1.5.em).minus(leftOffset)
                }

                +attributeName.value
            }
        }
    }


    private fun RBuilder.renderEgress(
            cardColor: Color
    ) {
        styledDiv {
            css {
                backgroundColor = cardColor
                position = Position.absolute

                width = 2.em
                height = 2.em

                bottom = (-2).em
                left = cardWidth.div(2).minus(1.em)
            }
        }

        styledDiv {
            css {
                position = Position.absolute

                width = 0.px
                height = 0.px

                borderTop(2.em, BorderStyle.solid, cardColor)
                borderLeft(2.em, BorderStyle.solid, Color.transparent)
                borderRight(2.em, BorderStyle.solid, Color.transparent)

                bottom = (-3).em
                left = cardWidth.div(2).minus(2.em)
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

                    bottom = (-5).px
                    left = cardWidth.div(2).minus(0.75.em)
                }
                attrs {
                    title = "Has more messages"
                }

                child(KeyboardArrowDownIcon::class) {}
            }
        }

        val vertexMessage = visualVertexModel()?.message
        if (vertexMessage != null) {
            val successors = props.dataflowDag.successors[props.vertexLocation()]
                    ?: listOf()

            val isMessagePending =
                    successors.isEmpty() ||
                            successors.any {
                                props.visualDataflowModel.vertices[it]!!.epoch == 0
                            }

            if (isMessagePending) {
                styledDiv {
                    css {
                        position = Position.absolute

                        width = 1.em
                        height = 1.em

                        bottom = (-1).em
                        left = cardWidth.div(2).minus(1.5.em)
                    }

                    child(MaterialIconButton::class) {
                        attrs {
                            title = "Message"

                            style = reactStyle {
                                color = Color.black

                                backgroundColor = Color("rgba(255, 215, 0, 0.175)")
//                                backgroundColor = Color("rgba(255, 215, 0, 0.5)")
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

                        bottom = (-2).em
                        left = cardWidth.div(2).plus(2.em)
                    }

                    attrs {
                        title = "Message content"
                    }

                    +"${vertexMessage.get()}"
                }
            }
        }
    }


    private fun RBuilder.renderEgressRight(
            cardColor: Color
    ) {
        styledDiv {
            css {
                backgroundColor = cardColor
                position = Position.absolute

                width = 2.em
                height = 2.em
                bottom = 0.em
                left = cardWidth.div(2).plus(1.em)
            }
        }

        styledDiv {
            css {
                position = Position.absolute

                width = 0.px
                height = 0.px

                borderLeft(2.em, BorderStyle.solid, cardColor)
                borderTop(2.em, BorderStyle.solid, Color.transparent)
                borderBottom(2.em, BorderStyle.solid, Color.transparent)

                bottom = (-1).em
                left = cardWidth.div(2).plus(2.em)
            }
        }
    }


    private fun RBuilder.renderIngressLeft(
            cardColor: Color
    ) {
        styledDiv {
            css {
                backgroundColor = cardColor
                position = Position.absolute

                width = 2.em
                height = 2.em
                bottom = 0.em
                left = cardWidth.div(2).minus(2.em)
            }
        }

        styledDiv {
            css {
                position = Position.absolute

                width = 0.px
                height = 0.px

                borderLeft(2.em, BorderStyle.solid, cardColor)
                borderTop(2.em, BorderStyle.solid, Color.transparent)
                borderBottom(2.em, BorderStyle.solid, Color.transparent)

                bottom = (-1).em
                left = cardWidth.div(2).minus(3.em)
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

        val name = props.vertexLocation()!!.objectPath.name
        val displayName =
                if (AutoConventions.isAnonymous(name)) {
                    val objectNotation = props.graphStructure.graphNotation.coalesce[props.vertexLocation()!!]!!
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


    private fun RBuilder.renderState() {
//        console.log("^^^^ renderState", props.visualVertexModel)
        val vertexState = visualVertexModel()?.state
                ?: return

        styledDiv {
            css {
                backgroundColor = Color("rgba(0, 0, 0, 0.05)")
            }
//            +"State: ${vertexState.get()}"
            +"${vertexState.get()}"
        }
    }
}