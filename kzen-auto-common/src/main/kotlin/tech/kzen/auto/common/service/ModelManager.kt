package tech.kzen.auto.common.service

import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.edit.NotationEvent
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest


class ModelManager(
        private var notationMediaCache: MapNotationMedia,
        private var notationRepository: NotationRepository,
        private var notationMedia: NotationMedia,
        private var notationMetadataReader: NotationMetadataReader
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun handleModel(autoModel: ProjectModel, event: NotationEvent?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Observer>()
    private var mostRecent: ProjectModel? = null


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(subscriber: Observer) {
        subscribers.add(subscriber)

//        if (mostRecent != null) {
//            subscriber.handleModel(mostRecent!!, null)
//        }

        subscriber.handleModel(projectModel(), null)
    }


    fun unobserve(subscriber: Observer) {
        subscribers.remove(subscriber)
    }


    private suspend fun publish(event: NotationEvent?) {
        println("ModelManager - Publishing - start")

        mostRecent = readModel()

        println("ModelManager - Publishing")
        for (subscriber in subscribers) {
            subscriber.handleModel(mostRecent!!, event)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun autoNotation(): GraphNotation {
        val allNotation = notationRepository.notation()

        return allNotation.filterPaths {
            it.asRelativeFile().startsWith("base/") ||
                    it.asRelativeFile().startsWith("auto/")
        }
    }


    private fun graphNotation(allNotation: GraphNotation): GraphNotation {
        return allNotation.filterPaths {
            it.asRelativeFile().startsWith("base/") ||
                    ! it.asRelativeFile().startsWith("auto/")
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

        val graphNotation = graphNotation(allNotation)
        println("^^^^^^ readModel - ${graphNotation.coalesce.values}")

        val metadata = notationMetadataReader.read(graphNotation)

        return ProjectModel(
                graphNotation,
                metadata)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        println("ModelManager - Refreshing - ${mostRecent == null}")

        val restScan = notationMedia.scan()
        val clientScan = notationMediaCache.scan()

        println("ModelManager - Refreshing - got scan - ${restScan.values.keys} vs ${clientScan.values.keys}")

        var changed = false
        for (dangling in clientScan.values.keys.minus(restScan.values.keys)) {
            notationMediaCache.delete(dangling)
            changed = true
        }

        for (e in restScan.values) {
            val clientDigest = clientScan.values[e.key]
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
    suspend fun onEvent(event: NotationEvent) {
        publish(event)
    }
}