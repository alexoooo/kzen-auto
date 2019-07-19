package tech.kzen.auto.client.objects.document.script

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.NameConventions
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.platform.collect.persistentListOf


@Suppress("unused")
class ScriptController:
        RPureComponent<ScriptController.Props, ScriptController.State>(),
        GraphStructureManager.Observer,
        ExecutionManager.Observer,
        InsertionManager.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun stepLocations(
                graphStructure: GraphStructure,
                documentPath: DocumentPath
        ): List<ObjectLocation>? {
            val mainObjectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)

            val stepsNotation = graphStructure
                    .graphNotation
                    .transitiveAttribute(mainObjectLocation, ScriptDocument.stepsAttributePath)
                    as? ListAttributeNotation
                    ?: return null

            return stepsNotation
                    .values
                    .map { ObjectReference.parse(it.asString()!!) }
                    .map { graphStructure.graphNotation.coalesce.locate(it) }
        }
    }


    class Props(
            var stepController: StepController.Wrapper
    ): RProps


    class State(
            var documentPath: DocumentPath?,
            var structure: GraphStructure?,
            var execution: ImperativeModel?,
            var creating: Boolean
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val type: DocumentArchetype,
            private val stepController: StepController.Wrapper
    ):
            DocumentController
    {
        override fun type(): DocumentArchetype {
            return type
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(ScriptController::class) {
                attrs {
                    this.stepController = this@Wrapper.stepController
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        documentPath = null
        structure = null
        execution = null
        creating = false
    }


    override fun componentDidMount() {
//        console.log("^^^^^^ script - componentDidMount")

//        println("ProjectController - Subscribed")
        async {
//            console.log("^^^^^^ script - adding observers")
            ClientContext.modelManager.observe(this)
            ClientContext.executionManager.observe(this)
            ClientContext.insertionManager.subscribe(this)
            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        console.log("^^^^^^ script - componentWillUnmount")

//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unobserve(this)
        ClientContext.insertionManager.unSubscribe(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
//        console.log("%#$%#$%#$ componentDidUpdate", state.documentPath, prevState.documentPath)
        if (state.documentPath != null && state.documentPath != prevState.documentPath) {
            async {
                val executionModel = ClientContext.executionManager.executionModel(state.documentPath!!)
//                console.log("%#$%#$%#$ componentDidUpdate",
//                        state.documentPath,
//                        prevState.documentPath,
//                        executionModel)

                setState {
                    execution = executionModel
                }
            }
        }

//        console.log("ProjectController componentDidUpdate", state, prevState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(graphStructure: GraphStructure, event: NotationEvent?) {
//        println("ProjectController - && handled - " +
//                "${autoModel.graphNotation.bundles.values[NotationConventions.mainPath]?.objects?.values?.keys}")

        setState {
            structure = graphStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {
        if (state.documentPath != host) {
            return
        }

//        console.log("^^^^ onExecutionModel: $host - ${state.documentPath} - $executionModel")
        setState {
            execution = executionModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSelected(action: ObjectLocation) {
        setState {
            creating = true
        }
    }


    override fun onUnselected() {
        setState {
            creating = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?) {
//        console.log("^^^^^^ script - handleNavigation", documentPath)

        setState {
            this.documentPath = documentPath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(index: Int) {
        val archetypeObjectLocation = ClientContext.insertionManager
                .getAndClearSelection()
                ?: return

        val containingObjectLocation = ObjectLocation(
                state.documentPath!!, NotationConventions.mainObjectPath)

        val newName = findNextAvailable(
                containingObjectLocation, archetypeObjectLocation)

        // NB: +1 offset for main Script object
        val endOfDocumentPosition =
                state.structure!!.graphNotation.documents.get(state.documentPath!!)!!.objects.values.size

        val objectNotation = ObjectNotation.ofParent(
                archetypeObjectLocation.toReference().name)

        val command = InsertObjectInListAttributeCommand(
                containingObjectLocation,
                ScriptDocument.stepsAttributePath,
                PositionIndex(index),
                newName,
                PositionIndex(endOfDocumentPosition),
                objectNotation
        )

        async {
            ClientContext.commandBus.apply(command)
        }
    }


    private fun toObjectPath(
            containingObjectLocation: ObjectLocation,
            objectName: ObjectName
    ): ObjectPath {
        return containingObjectLocation.objectPath.nest(
                ScriptDocument.stepsAttributePath, objectName)
    }


    private fun findNextAvailable(
            containingObjectLocation: ObjectLocation,
            archetypeObjectLocation: ObjectLocation
    ): ObjectName {
        val namePrefix = state.structure
                ?.graphNotation
                ?.transitiveAttribute(archetypeObjectLocation, AutoConventions.titleAttributePath)
                ?.asString()
                ?: archetypeObjectLocation.objectPath.name.value

        val directObjectName = ObjectName(namePrefix)
        val directObjectPath = toObjectPath(containingObjectLocation, directObjectName)

        val documentObjects =
                state.structure!!.graphNotation.documents.get(state.documentPath!!)!!.objects

        if (! documentObjects.values.containsKey(directObjectPath)) {
            return directObjectName
        }

        for (i in 2 .. 1000) {
            val numberedObjectName = ObjectName("$namePrefix $i")
            val numberedObjectPath = toObjectPath(containingObjectLocation, numberedObjectName)

            if (! documentObjects.values.containsKey(numberedObjectPath)) {
                return numberedObjectName
            }
        }

        return NameConventions.randomAnonymous()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val structure = state.structure
                ?: return

        val documentPath: DocumentPath = state.documentPath
                ?: return

        styledDiv {
            css {
                marginLeft = 2.em
            }

            steps(structure, documentPath)
        }

        runController()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            graphStructure: GraphStructure,
            documentPath: DocumentPath
    ) {
        val stepLocations = stepLocations(graphStructure, documentPath)
                ?: return

        if (stepLocations.isEmpty()) {
            styledDiv {
                css {
                    paddingTop = 2.em
                }

                styledDiv {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Empty script, please add steps from the toolbar (above)"
                }

                insertionPoint(0)
            }
        }
        else {
            styledDiv {
                css {
                    paddingLeft = 1.em
                }
                nonEmptySteps(graphStructure, documentPath, stepLocations)
            }
        }
    }


    private fun RBuilder.nonEmptySteps(
            graphStructure: GraphStructure,
            documentPath: DocumentPath,
            stepLocations: List<ObjectLocation>
    ) {
        insertionPoint(0)

        styledDiv {
            css {
                width = 20.em
            }

            for ((index, stepLocation) in stepLocations.withIndex()) {
                val objectPath = stepLocation.objectPath

                val executionState: ImperativeState? =
                        state.execution?.frames?.lastOrNull()?.states?.get(objectPath)

                val keyLocation = ObjectLocation(documentPath, objectPath)

                action(index,
                        keyLocation,
                        graphStructure,
                        executionState)

                if (index < stepLocations.size - 1) {
                    downArrowWithInsertionPoint(index + 1)
                }
            }
        }

        insertionPoint(stepLocations.size)
    }


    private fun RBuilder.downArrowWithInsertionPoint(index: Int) {
        styledDiv {
            css {
                position = Position.relative
                height = 4.em
                width = 9.em
            }

            styledDiv {
                css {
                    marginTop = 0.5.em

                    position = Position.absolute
                    height = 1.em
                    width = 1.em
                    top = 0.em
                    left = 0.em
                }
                insertionPoint(index)
            }

            styledDiv {
                css {
                    position = Position.absolute
                    height = 3.em
                    width = 3.em
                    top = 0.em
                    left = 8.5.em

                    marginTop =  0.5.em
                    marginBottom = 0.5.em
                }

                child(ArrowDownwardIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.insertionPoint(index: Int) {
        styledSpan {
            attrs {
                if (state.creating) {
                    title = "Insert action here"
                }
            }

//            +"Index: $index"

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        if (! state.creating) {
                            opacity = 0
                            cursor = Cursor.default
                        }
                    }

                    onClick = {
                        onCreate(index)
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.action(
            index: Int,
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            executionState: ImperativeState?
    ) {
        span {
            key = objectLocation.toReference().asString()

            props.stepController.child(this) {
                attrs {
                    attributeNesting = AttributeNesting(persistentListOf(AttributeSegment.ofIndex(index)))
                    this.objectLocation = objectLocation
                    this.graphStructure = graphStructure
                    imperativeState = executionState
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.runController() {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(ScriptRunController::class) {
                attrs {
                    documentPath = state.documentPath
                    structure = state.structure
                    execution = state.execution
                }
            }
        }
    }

//    private fun RBuilder.refresh() {
//        input (type = InputType.button) {
//            attrs {
//                value = "Reload"
//                onClickFunction = { onRefresh() }
//            }
//        }
//    }
}