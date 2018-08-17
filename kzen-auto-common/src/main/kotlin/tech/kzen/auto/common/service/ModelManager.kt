package tech.kzen.auto.common.service

import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest


class ModelManager(
        private var notationMediaCache: MapNotationMedia,
        private var notationRepository: NotationRepository,
        private var notationMedia: NotationMedia,
        private var notationMetadataReader: NotationMetadataReader
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun handleModel(autoModel: ProjectModel, event: ProjectEvent?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Subscriber>()
    private var mostRecent: ProjectModel? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)

        if (mostRecent != null) {
            subscriber.handleModel(mostRecent!!, null)
        }
    }


    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }


    private suspend fun publish(event: ProjectEvent?) {
        println("ModelManager - Publishing - start")

        mostRecent = readModel()

        println("ModelManager - Publishing")
        for (subscriber in subscribers) {
            subscriber.handleModel(mostRecent!!, event)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun autoNotation(): ProjectNotation {
        val allNotation = notationRepository.notation()

        return allNotation.filterPaths {
            it.relativeLocation.startsWith("notation/base/") ||
                    it.relativeLocation.startsWith("notation/auto/")
        }
    }


    private fun projectNotation(allNotation: ProjectNotation): ProjectNotation {
        return allNotation.filterPaths {
            it.relativeLocation.startsWith("notation/base/") ||
//                    (! it.relativeLocation.startsWith("notation/auto/") ||
//                            it.relativeLocation.endsWith("auto-common.yaml") ||
//                            it.relativeLocation.endsWith("auto-browser.yaml"))
                    ! it.relativeLocation.startsWith("notation/auto/")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun projectModel(): ProjectModel {
        if (mostRecent == null) {
            mostRecent = readModel()
        }
        return mostRecent!!
    }


    private suspend fun readModel(): ProjectModel {
        val allNotation = notationRepository.notation()

        val projectNotation = projectNotation(allNotation)

        val metadata = notationMetadataReader.read(projectNotation)

        return ProjectModel(
                projectNotation,
                metadata)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        println("ModelManager - Refreshing - ${mostRecent == null}")

        val restScan = notationMedia.scan()
        val clientScan = notationMediaCache.scan()

        println("ModelManager - Refreshing - got scan - ${restScan.keys} vs ${clientScan.keys}")

        var changed = false
        for (dangling in clientScan.keys.minus(restScan.keys)) {
            notationMediaCache.delete(dangling)
            changed = true
        }

        for (e in restScan) {
            val clientDigest = clientScan[e.key]
                    ?: Digest.zero

            if (clientDigest != e.value) {
                println("ModelManager - Saving - ${e.key}")

                val body = notationMedia.read(e.key)

                println("ModelManager - read - ${body.size}")

                notationMediaCache.write(e.key, body)
                changed = true
            }
        }

        println("ModelManager - Refreshing check - $changed - ${mostRecent == null}")
        if (changed || mostRecent == null) {
            notationRepository.clearCache()
            publish(null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun onEvent(event: ProjectEvent) {
        publish(event)
    }
}