package tech.kzen.auto.client.service

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.structure.notation.edit.*
import kotlin.browser.window


class NavigationManager: CommandBus.Subscriber
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
//        console.log("^^^^ nav publishing")
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
    fun postConstruct(commandBus: CommandBus) {
        // var type = window.location.hash.substr(1);

        readAndPublishIfNecessary()

        window.addEventListener("hashchange", {
//            console.log("^^^ hashchange", it)
            readAndPublishIfNecessary()
        })

        commandBus.subscribe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onCommandFailedInClient(command: NotationCommand, cause: Throwable) {}


    override fun onCommandSuccess(command: NotationCommand, event: NotationEvent) {
        when (command) {
            is RenameDocumentRefactorCommand -> {
                val renamedEvent = event as RenamedDocumentRefactorEvent
//                console.log("^^^^ RenameDocumentRefactorCommand",
//                        documentPath?.asString(),
//                        renamedEvent.removedUnderOldName.documentPath.asString(),
//                        renamedEvent.createdWithNewName.destination.asString(),
//                        event)
                if (renamedEvent.removedUnderOldName.documentPath == documentPath) {
                    goto(renamedEvent.createdWithNewName.destination)
                }
            }


            is DeleteDocumentCommand ->
                if (command.documentPath == documentPath) {
                    clear()
                }


            // NB: could have been pre-selected before creation
            is CreateDocumentCommand -> {
                if (command.documentPath == documentPath) {
                    publish()
                }
            }
        }
    }


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