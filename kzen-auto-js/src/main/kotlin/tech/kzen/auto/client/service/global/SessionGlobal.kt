package tech.kzen.auto.client.service.global

import tech.kzen.auto.client.objects.ribbon.RibbonRun
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.service.store.LocalGraphStore


class SessionGlobal:
        NavigationGlobal.Observer,
        ExecutionRepository.Observer,
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onClientState(clientState: SessionState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var sessionState: SessionState? = null

    private var graphDefinitionAttempt: GraphDefinitionAttempt? = null
    private var navigationRoute: NavigationRoute? = null
//    private val imperativeModels = mutableMapOf<DocumentPath, ImperativeModel>()
    private var imperativeModel: ImperativeModel? = null
    private var runningHosts = setOf<DocumentPath>()


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun postConstruct(
            navigationGlobal: NavigationGlobal,
            executionRepository: ExecutionRepository,
            localGraphStore: LocalGraphStore,
            clientRestApi: ClientRestApi
    ) {
        runningHosts = clientRestApi.runningHosts().toSet()
//        console.log("%%%%% runningHosts - $runningHosts")

        localGraphStore.observe(this)
        executionRepository.observe(this)
        navigationGlobal.observe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?, parameters: RequestParams) {
        navigationRoute = NavigationRoute(documentPath, parameters)

        val selected = parameters.get(RibbonRun.runningKey)?.let { DocumentPath.parse(it) }
        if (selected != null) {
            async {
                imperativeModel = ClientContext.executionRepository.executionModel(
                        selected, graphDefinitionAttempt!!.successful.graphStructure)
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
            runningHosts -= event.documentPath
        }

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

        val selected = navigation.requestParams.get(RibbonRun.runningKey)?.let { DocumentPath.parse(it) }

//        val imperativeModel =
//                if (selected != null) {
//                    imperativeModels[selected]
//                            ?: return
//                }
//                else {
//                    imperativeModels[navigation.documentPath]
//                }

        sessionState = SessionState(
                definition,
                navigation,
                imperativeModel,
                selected,
                runningHosts)

        for (observer in observers) {
            observer.onClientState(sessionState!!)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------

}