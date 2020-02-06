package tech.kzen.auto.client.service.global

import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionLoop
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import kotlin.browser.window


class NavigationGlobal(
        private val executionLoop: ExecutionLoop,
        private val visualDataflowLoop: VisualDataflowLoop
):
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun handleNavigation(
                documentPath: DocumentPath?,
                parameters: RequestParams)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var documentPath: DocumentPath? = null
    private var parameters: RequestParams = RequestParams.empty


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
        executionLoop.pauseAll()
        visualDataflowLoop.pauseAll()

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
            readAndPublishIfNecessary()
        })

//        commandBus.subscribe(this)
        commandBus.observe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        when (event) {
            is RenamedDocumentRefactorEvent -> {
                if (event.removedUnderOldName.documentPath == documentPath) {
                    goto(event.createdWithNewName.destination)
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
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    fun clear() {
        window.location.hash = ""
    }


    fun goto(documentPath: DocumentPath) {
        window.location.hash = "#" + documentPath.asString()
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

        val locationHash = js("decodeURI(encodedLocationHash)") as String

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

        documentPath = DocumentPath.parse(path)
        parameters = params
    }
}