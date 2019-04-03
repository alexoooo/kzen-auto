package tech.kzen.auto.client.service

import tech.kzen.lib.common.model.document.DocumentPath
import kotlin.browser.window


class NavigationManager {
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
    fun postConstruct() {
        // var type = window.location.hash.substr(1);

        readAndPublishIfNecessary()

        window.addEventListener("hashchange", {
//            console.log("^^^ hashchange", it)
            readAndPublishIfNecessary()
        })
    }


    fun goto(documentPath: DocumentPath) {
        window.location.hash = "#" + documentPath.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun readAndPublishIfNecessary() {
        val previous = documentPath

        documentPath = read()

        if (previous != documentPath) {
//            async {
                publish()
//            }
        }
    }


    private fun read(): DocumentPath? {
//        console.log("^^^ read", window.location.hash)
        val path = window.location.hash.substring(1)

        if (path.isEmpty()) {
            return null
        }

        return DocumentPath.parse(path)
    }
}