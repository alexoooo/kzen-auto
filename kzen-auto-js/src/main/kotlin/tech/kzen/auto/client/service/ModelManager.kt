package tech.kzen.auto.client.service

import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest


class ModelManager(
        private var clientNotationMedia: MapNotationMedia,
        private var clientRepository: NotationRepository,
        private var restNotationMedia: NotationMedia,
        private var notationMetadataReader: NotationMetadataReader
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun handle(autoModel: ProjectModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Subscriber>()
    private var mostRecent: ProjectModel? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)

        if (mostRecent != null) {
            subscriber.handle(mostRecent!!)
        }
    }


    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }


    private suspend fun publish() {
        println("ModelManager - Publishing - start")

        val allNotation = clientRepository.notation()

        val projectNotation = projectNotation(allNotation)

        val metadata = notationMetadataReader.read(projectNotation)

        mostRecent = ProjectModel(
                projectNotation,
                metadata)

        println("ModelManager - Publishing")
        for (subscriber in subscribers) {
            subscriber.handle(mostRecent!!)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun autoNotation(): ProjectNotation {
        val allNotation = clientRepository.notation()

        return allNotation.filterPaths {
            it.relativeLocation.startsWith("notation/base/") ||
                    it.relativeLocation.startsWith("notation/auto/")
        }
    }


    private fun projectNotation(allNotation: ProjectNotation): ProjectNotation {
        return allNotation.filterPaths {
            it.relativeLocation.startsWith("notation/base/") ||
                    (! it.relativeLocation.startsWith("notation/auto/") ||
                            it.relativeLocation.endsWith("auto-common.yaml"))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun refresh() {
        println("ModelManager - Refreshing - ${mostRecent == null}")

        val restScan = restNotationMedia.scan()
        val clientScan = clientNotationMedia.scan()

        println("ModelManager - Refreshing - got scan - ${restScan.keys} vs ${clientScan.keys}")

        var changed = false
        for (dangling in clientScan.keys.minus(restScan.keys)) {
            clientNotationMedia.delete(dangling)
            changed = true
        }

        for (e in restScan) {
            val clientDigest = clientScan[e.key]
                    ?: Digest.zero

            if (clientDigest != e.value) {
                println("ModelManager - Saving - ${e.key}")

                val body = restNotationMedia.read(e.key)

                println("ModelManager - read - ${body.size}")

                clientNotationMedia.write(e.key, body)
                changed = true
            }
        }

        println("ModelManager - Refreshing check - $changed - ${mostRecent == null}")
        if (changed || mostRecent == null) {
            clientRepository.clearCache()
            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun onEvent(event: ProjectEvent) {
        publish()
    }
}