package tech.kzen.auto.client.service.global

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.service.store.LocalGraphStore


class SessionGlobal:
        NavigationGlobal.Observer,
        ExecutionRepository.Observer,
        ClientLogicGlobal.Observer,
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val runningKey = "running"
    }

    interface Observer {
        fun onClientState(clientState: SessionState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var sessionState: SessionState? = null

    private var graphDefinitionAttempt: GraphDefinitionAttempt? = null
    private var navigationRoute: NavigationRoute? = null

    private var clientLogicState: ClientLogicState? = null

//    private val imperativeModels = mutableMapOf<DocumentPath, ImperativeModel>()
    private var imperativeModel: ImperativeModel? = null
    private var runningHosts = setOf<DocumentPath>()



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun postConstruct(
            navigationGlobal: NavigationGlobal,
            localGraphStore: LocalGraphStore,
            clientLogicGlobal: ClientLogicGlobal,
            clientRestApi: ClientRestApi,
            executionRepository: ExecutionRepository
    ) {
        runningHosts = clientRestApi.runningHosts().toSet()
//        console.log("%%%%% runningHosts - $runningHosts")

        localGraphStore.observe(this)
        navigationGlobal.observe(this)
        clientLogicGlobal.observe(this)

        executionRepository.observe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?, parameters: RequestParams) {
        navigationRoute = NavigationRoute(documentPath, parameters)

        val selected = parameters.get(runningKey)?.let { DocumentPath.parse(it) }
        if (selected != null) {
            async {
                imperativeModel = ClientContext.executionRepository.executionModel(
                        selected, graphDefinitionAttempt!!.graphStructure)
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


    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
        // TODO
    }


    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel?) {
//        console.log("^^ onExecutionModel - $host - $executionModel")

//        imperativeModels[host] = executionModel
        imperativeModel = executionModel

        val modifiedActive =
            if (executionModel?.isActive() == true) {
                runningHosts + host
            }
            else {
                runningHosts - host
            }

        runningHosts = modifiedActive

        publishIfReady()
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {

    }


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        if ((event is DeletedDocumentEvent || event is RenamedDocumentRefactorEvent)) {
            runningHosts = runningHosts - event.documentPath
        }

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

        val selected = navigation.requestParams.get(runningKey)?.let { DocumentPath.parse(it) }

        val nextSessionState = SessionState(
            definition,
            navigation,
            logicState,
            imperativeModel,
            selected,
            runningHosts)

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


    fun current(): SessionState? {
        return sessionState
    }
}