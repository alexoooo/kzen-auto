package tech.kzen.auto.client.service

import tech.kzen.lib.common.api.model.BundlePath
import kotlin.browser.window


class NavigationManager {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun handleNavigation(bundlePath: BundlePath?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var bundlePath: BundlePath? = null


    fun observe(observer: Observer) {
        observers.add(observer)

        if (bundlePath != null) {
            observer.handleNavigation(bundlePath)
        }
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun publish() {
        for (observer in observers) {
            observer.handleNavigation(bundlePath)
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


    fun goto(bundlePath: BundlePath) {
        window.location.hash = "#" + bundlePath.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun readAndPublishIfNecessary() {
        val previous = bundlePath

        bundlePath = read()

        if (previous != bundlePath) {
            publish()
        }
    }


    private fun read(): BundlePath? {
//        console.log("^^^ read", window.location.hash)
        val path = window.location.hash.substring(1)

        if (path.isEmpty()) {
            return null
        }

        return BundlePath.parse(path)
    }
}