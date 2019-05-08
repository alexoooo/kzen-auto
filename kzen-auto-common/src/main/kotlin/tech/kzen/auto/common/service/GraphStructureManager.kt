package tech.kzen.auto.common.service

import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest


// TODO: add profiles or something instead of using ad-hoc document path patterns
class GraphStructureManager(
        private var notationMediaCache: MapNotationMedia,
        private var notationRepository: NotationRepository,
        private var notationMedia: NotationMedia,
        private var notationMetadataReader: NotationMetadataReader
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun handleModel(graphStructure: GraphStructure, event: NotationEvent?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Observer>()
    private var serverGraphStructureCache: GraphStructure? = null


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(observer: Observer) {
        subscribers.add(observer)

//        if (mostRecent != null) {
//            subscriber.handleModel(mostRecent!!, null)
//        }

        observer.handleModel(serverGraphStructure(), null)
    }


    fun unobserve(subscriber: Observer) {
        subscribers.remove(subscriber)
    }


    private suspend fun publish(event: NotationEvent?) {
//        println("ModelManager - Publishing - start")

        serverGraphStructureCache = readServerGraphStructure()

//        println("ModelManager - Publishing - $mostRecent")
        for (subscriber in subscribers) {
            subscriber.handleModel(serverGraphStructureCache!!, event)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun clientGraphStructure(): GraphStructure {
        val allNotation = notationRepository.notation()
        val clientGraphNotation = clientGraphNotation(allNotation)
        val metadata = notationMetadataReader.read(clientGraphNotation)
        return GraphStructure(clientGraphNotation, metadata)
    }


    private fun clientGraphNotation(
            allNotation: GraphNotation
    ): GraphNotation {
        // TODO: use profiles instead
        return allNotation.filterPaths {
            it.asRelativeFile().startsWith("base/") ||
                    it.asRelativeFile().startsWith("auto-js/") ||
                    it.asRelativeFile().startsWith("auto-common/") ||
                    it.asRelativeFile().startsWith("auto-jvm/")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun serverGraphStructure(): GraphStructure {
        if (serverGraphStructureCache == null) {
            serverGraphStructureCache = readServerGraphStructure()
        }
        return serverGraphStructureCache!!
    }


    private suspend fun readServerGraphStructure(): GraphStructure {
        val allNotation = notationRepository.notation()

        val graphNotation = serverGraphNotation(allNotation)
//        println("^^^^^^ readModel ^^ - ${graphNotation.coalesce.values}")

        val metadata = notationMetadataReader.read(graphNotation)
//        println("^^^^^^ readModel - got metadata: $metadata")

        return GraphStructure(
                graphNotation,
                metadata)
    }


    private fun serverGraphNotation(allNotation: GraphNotation): GraphNotation {
        return allNotation.filterPaths {
            it.asRelativeFile().startsWith("base/") ||
                    ! it.asRelativeFile().startsWith("auto-js/")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
//        println("ModelManager - Refreshing - ${mostRecent == null}")

        val restScan = notationMedia.scan()
        val clientScan = notationMediaCache.scan()

//        println("ModelManager - Refreshing - got scan - ${restScan.values.keys} vs ${clientScan.values.keys}")

        var changed = false
        for (dangling in clientScan.values.keys.minus(restScan.values.keys)) {
            notationMediaCache.delete(dangling)
            changed = true
        }

        for (e in restScan.values) {
            val clientDigest = clientScan.values[e.key]
                    ?: Digest.zero

            if (clientDigest != e.value) {
//                println("ModelManager - Saving - ${e.key}")

                val body = notationMedia.read(e.key)

//                println("ModelManager - read - ${body.size}")

                notationMediaCache.write(e.key, body)
                changed = true
            }
        }

//        println("ModelManager - Refreshing check - $changed - ${mostRecent == null}")
        if (changed || serverGraphStructureCache == null) {
            notationRepository.clearCache()
            publish(null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun onEvent(event: NotationEvent) {
        publish(event)
    }
}