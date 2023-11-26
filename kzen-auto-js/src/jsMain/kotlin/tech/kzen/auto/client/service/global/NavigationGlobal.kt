package tech.kzen.auto.client.service.global

import kotlinx.browser.window
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.platform.decodeURI
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore


class NavigationGlobal(
//        private val executionLoop: ExecutionLoop,
        private val visualDataflowLoop: VisualDataflowLoop
):
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun handleNavigation(
            documentPath: DocumentPath?,
            parameters: RequestParams
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var documentPath: DocumentPath? = null
    private var parameters: RequestParams = RequestParams.empty
    private var returnPending: Boolean = false


    fun observe(observer: Observer) {
        observers.add(observer)

        if (documentPath != null) {
            observer.handleNavigation(documentPath, parameters)
        }
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun publish() {
        if (returnPending) {
            // TODO: consolidate loops
//            executionLoop.pauseAll()
            visualDataflowLoop.pauseAll()

            returnPending = false
        }

        val observersCopy = observers.toList()
        for (observer in observersCopy) {
            if (observer !in observers) {
                // NB: unobserve could be called mid-way through iteration, e.g. in StageController / ScriptController
                continue
            }

//            console.log("^^^^ nav publishing - observer", observer)
            observer.handleNavigation(documentPath, parameters)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun postConstruct(commandBus: MirroredGraphStore) {
        // var type = window.location.hash.substr(1);

        readAndPublishIfNecessary()

        window.addEventListener("hashchange", {
//            console.log("^^^ hashchange", it)

            // TODO: get chrome error here: [Violation] 'hashchange' handler took <N>ms
            readAndPublishIfNecessary()
        })

//        commandBus.subscribe(this)
        commandBus.observe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        when (event) {
            is RenamedDocumentRefactorEvent -> {
                if (event.removedUnderOldName.documentPath == documentPath) {
                    val updatedParameters = parameters.replaceValues(
                        event.removedUnderOldName.documentPath.asString(),
                        event.createdWithNewName.destination.asString())

                    if (parameters == updatedParameters) {
                        goto(event.createdWithNewName.destination)
                    }
                    else {
                        goto(event.createdWithNewName.destination, updatedParameters)
                    }
                }
            }


            is DeletedDocumentEvent ->
                if (event.documentPath == documentPath) {
                    clear()
                }


            // NB: could have been pre-selected before creation
            is CreatedDocumentEvent -> {
                if (event.documentPath == documentPath) {
                    publish()
                }
            }

            else -> {}
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    fun clear() {
        window.location.hash = ""
    }


    fun goto(newDocumentPath: DocumentPath) {
        window.location.hash = NavigationRoute(newDocumentPath, parameters).toFragment()
    }

    fun goto(newDocumentPath: DocumentPath, newParameters: RequestParams) {
        window.location.hash = NavigationRoute(newDocumentPath, newParameters).toFragment()
    }


    fun returnTo(documentPath: DocumentPath) {
        returnPending = true
        goto(documentPath)
    }


    fun parameterize(requestParams: RequestParams) {
        window.location.hash = NavigationRoute(documentPath, requestParams).toFragment()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun readAndPublishIfNecessary() {
        val previousPath = documentPath
        val previousParameters = parameters

        readPathAndParameters()

        if (previousPath != documentPath ||
                previousParameters != parameters) {
            publish()
        }
    }


    private fun readPathAndParameters() {
//        console.log("^^^ read", window.location.hash)
        val encodedLocationHash = window.location.hash.substring(1)

        if (encodedLocationHash.isEmpty()) {
            documentPath = null
            parameters = RequestParams.empty
            return
        }

        val locationHash = decodeURI(encodedLocationHash)

        val paramIndex = locationHash.indexOf('?')

        val path =
                if (paramIndex == -1) {
                    locationHash
                }
                else {
                    locationHash.substring(0, paramIndex)
                }

        val params =
                if (paramIndex == -1) {
                    RequestParams.empty
                }
                else {
                    val paramSuffix = locationHash.substring(paramIndex + 1)
                    RequestParams.parse(paramSuffix)
                }

        documentPath =
            if (path.isEmpty()) {
                null
            }
            else {
                DocumentPath.parse(path)
            }

        parameters = params
    }
}