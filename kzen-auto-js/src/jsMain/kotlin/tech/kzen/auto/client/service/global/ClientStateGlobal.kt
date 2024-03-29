package tech.kzen.auto.client.service.global

import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.store.LocalGraphStore


class ClientStateGlobal:
    NavigationGlobal.Observer,
    ClientLogicGlobal.Observer,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val runningKey = "running"
    }

    interface Observer {
        fun onClientState(clientState: ClientState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var sessionState: ClientState? = null

    private var graphDefinitionAttempt: GraphDefinitionAttempt? = null
    private var navigationRoute: NavigationRoute? = null

    private var clientLogicState: ClientLogicState? = null


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun postConstruct(
        navigationGlobal: NavigationGlobal,
        localGraphStore: LocalGraphStore,
        clientLogicGlobal: ClientLogicGlobal
    ) {
        localGraphStore.observe(this)
        navigationGlobal.observe(this)
        clientLogicGlobal.observe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?, parameters: RequestParams) {
        navigationRoute = NavigationRoute(documentPath, parameters)

        val selected = parameters.get(runningKey)?.let { DocumentPath.parse(it) }
        if (selected != null) {
            async {
//                imperativeModel = ClientContext.executionRepository.executionModel(
//                        selected, graphDefinitionAttempt!!.graphStructure)
                publishIfReady()
            }
        }
        else {
//            val isScript = ScriptDocument.isScript(
//                    graphDefinitionAttempt!!.successful.graphStructure.graphNotation.documents.values[documentPath]!!)
//
//            if (documentPath != null && isScript) {
//                async {
//                    ClientContext.executionRepository.executionModel(
//                            documentPath, graphDefinitionAttempt!!.successful.graphStructure)
//                    publishIfReady()
//                }
//            }
//            else {
                publishIfReady()
//            }
        }
    }


//    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
//        // TODO
//    }
//
//
//    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel?) {
////        console.log("^^ onExecutionModel - $host - $executionModel")
//
////        imperativeModels[host] = executionModel
////        imperativeModel = executionModel
//
////        val modifiedActive =
////            if (executionModel?.isActive() == true) {
////                runningHosts + host
////            }
////            else {
////                runningHosts - host
////            }
////
////        runningHosts = modifiedActive
//
//        publishIfReady()
//    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {

    }


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
//        if ((event is DeletedDocumentEvent || event is RenamedDocumentRefactorEvent)) {
//            runningHosts = runningHosts - event.documentPath
//        }

//        console.log("^^ onCommandSuccess - $event")

//        when (event) {
//            is RenamedDocumentRefactorEvent -> {
//                if (event.removedUnderOldName.documentPath == state.value?.documentPath) {
//                    val newLocation =
//                            state.value!!.copy(documentPath = event.createdWithNewName.destination)
//
//                    setState {
//                        value = newLocation
//                        renaming = true
//                    }
//                }
//            }
//        }

        graphDefinitionAttempt = graphDefinition
        publishIfReady()
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        graphDefinitionAttempt = graphDefinition
        publishIfReady()
    }


    override fun onLogic(clientLogicState: ClientLogicState) {
        this.clientLogicState = clientLogicState
        publishIfReady()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)

        val initialState = sessionState
            ?: return

        observer.onClientState(initialState)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun publishIfReady() {
        val definition = graphDefinitionAttempt
            ?: return

        val navigation = navigationRoute
            ?: return

        val logicState = clientLogicState
            ?: return

//        val selected = navigation.requestParams.get(runningKey)?.let { DocumentPath.parse(it) }

        val nextSessionState = ClientState(
            definition,
            navigation,
            logicState
        )

        sessionState = nextSessionState

        for (observer in observers) {
            try {
                observer.onClientState(nextSessionState)
            }
            catch (e: Throwable) {
//                println("onClientState ERROR for $observer")
                e.printStackTrace()
            }
        }
    }


    fun current(): ClientState? {
        return sessionState
    }
}