package tech.kzen.auto.client.service

import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionLoop
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import kotlin.browser.window


class NavigationManager(
        private val executionLoop: ExecutionLoop,
        private val visualDataflowLoop: VisualDataflowLoop
):
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun handleNavigation(documentPath: DocumentPath?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var documentPath: DocumentPath? = null


    fun observe(observer: Observer) {
        observers.add(observer)

        if (documentPath != null) {
            observer.handleNavigation(documentPath)
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
            observer.handleNavigation(documentPath)
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
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinition) {
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


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinition) {}


    //-----------------------------------------------------------------------------------------------------------------
    fun clear() {
        window.location.hash = ""
    }


    fun goto(documentPath: DocumentPath) {
        window.location.hash = "#" + documentPath.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun readAndPublishIfNecessary() {
        val previous = documentPath

        documentPath = read()

        if (previous != documentPath) {
            publish()
        }
    }


    private fun read(): DocumentPath? {
//        console.log("^^^ read", window.location.hash)
        val encodedPath = window.location.hash.substring(1)

        if (encodedPath.isEmpty()) {
            return null
        }

//        val path = js("decodeURIComponent(encodedPath)") as String
        val path = js("decodeURI(encodedPath)") as String

        return DocumentPath.parse(path)
    }
}