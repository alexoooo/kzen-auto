package tech.kzen.auto.client.objects.document.query

import react.*
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.api.model.ObjectPath


class QueryController:
        RComponent<RProps, QueryController.State>()//,
//        ModelManager.Observer,
//        ExecutionManager.Observer,
//        InsertionManager.Observer,
//        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val mainObjectPath = ObjectPath.parse("main")
//        private val stepsAttributePath = AttributePath.parse("steps")
    }


    //-----------------------------------------------------------------------------------------------------------------
    class State(
//            var documentPath: DocumentPath?,
//            var structure: GraphStructure?,
//            var execution: ExecutionModel?,
//            var creating: Boolean
    ) : RState


    @Suppress("unused")
    class Wrapper(
            private val type: DocumentArchetype
    ) :
            DocumentController {
        override fun type(): DocumentArchetype {
            return type
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(QueryController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
//        async {
//            ClientContext.modelManager.observe(this)
//            ClientContext.executionManager.subscribe(this)
//            ClientContext.insertionManager.subscribe(this)
//            ClientContext.navigationManager.observe(this)
//        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
//        ClientContext.modelManager.unobserve(this)
//        ClientContext.executionManager.unsubscribe(this)
//        ClientContext.insertionManager.unSubscribe(this)
//        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: RProps,
            prevState: QueryController.State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
//        setState {
//            structure = projectStructure
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun beforeExecution(objectLocation: ObjectLocation) {}
//
//
//    override suspend fun onExecutionModel(executionModel: ExecutionModel) {
//        setState {
//            execution = executionModel
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun onSelected(action: ObjectLocation) {
//        setState {
//            creating = true
//        }
//    }
//
//
//    override fun onUnselected() {
//        setState {
//            creating = false
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun handleNavigation(documentPath: DocumentPath?) {
//        setState {
//            this.documentPath = documentPath
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onCreate(index: Int) {
//        async {
//            ClientContext.insertionManager.create(
//                    state.documentPath!!,
//                    index)
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        +"Query!"
    }
}