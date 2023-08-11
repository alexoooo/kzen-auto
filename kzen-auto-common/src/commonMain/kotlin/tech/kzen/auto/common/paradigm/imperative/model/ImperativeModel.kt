package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.toPersistentList


data class ImperativeModel(
        val running: ObjectLocation?,
        val frames: PersistentList<ImperativeFrame>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val runningKey = "running"
        private const val framesKey = "frames"


        val empty = ImperativeModel(null, persistentListOf())


        fun toCollection(model: ImperativeModel): Map<String, Any?> {
            val framesCollection = model
                    .frames
                    .map { ImperativeFrame.toCollection(it) }

            return mapOf(
                    runningKey to model.running?.asString(),
                    framesKey to framesCollection)
        }

//        fun toCollection(model: ImperativeModel): List<Map<String, Any>> {
//            return model
//                    .frames
//                    .map { ImperativeFrame.toCollection(it) }
//        }


        fun fromCollection(
                collection: Map<String, Any?>
        ): ImperativeModel {
            val runningValue = collection[runningKey] as? String
            val running = runningValue?.let { ObjectLocation.parse(it) }

            @Suppress("UNCHECKED_CAST")
            val framesCollection = collection[framesKey] as List<Map<String, Any>>
            val frames = framesCollection
                    .map { ImperativeFrame.fromCollection(it) }
                    .toPersistentList()

            return ImperativeModel(running, frames)
        }

//        fun fromCollection(
//                collection: List<Map<String, Any>>
//        ): ImperativeModel {
//            return ImperativeModel(collection
//                    .map { ImperativeFrame.fromCollection(it) }
//                    .toPersistentList())
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun move(from: DocumentPath, newPath: DocumentPath): ImperativeModel {
        var builder = frames

        for ((index, frame) in frames.withIndex()) {
            if (frame.path != from) {
                continue
            }

            val moved = frame.copy(path = newPath)
            builder = builder.set(index, moved)
        }

        if (builder == frames) {
            return this
        }

        val nextRunning =
                if (running?.documentPath == from) {
                    running.copy(documentPath = newPath)
                }
                else {
                    running
                }

        return ImperativeModel(nextRunning, builder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun remove(objectLocation: ObjectLocation): ImperativeModel {
        var builder = frames

        for ((index, frame) in frames.withIndex()) {
            if (frame.path != objectLocation.documentPath) {
                continue
            }

            val removed = frame.remove(objectLocation.objectPath)
            builder = builder.set(index, removed)
        }

        if (builder == frames) {
            return this
        }

        val nextRunning =
                if (running == objectLocation) {
                    null
                }
                else {
                    running
                }

        return ImperativeModel(nextRunning, builder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectLocation, newObjectPath: ObjectPath): ImperativeModel {
        var builder = frames
        for ((index, frame) in frames.withIndex()) {
            if (frame.path != from.documentPath) {
                continue
            }

            val renamed = frame.rename(from.objectPath, newObjectPath)
            builder = builder.set(index, renamed)
        }

        if (builder == frames) {
            return this
        }

        val nextRunning =
                if (running == from) {
                    running.copy(objectPath = newObjectPath)
                }
                else {
                    running
                }

        return ImperativeModel(nextRunning, builder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(objectLocation: ObjectLocation, state: ImperativeState): ImperativeModel {
        var builder = frames
        for ((index, frame) in frames.withIndex()) {
            if (frame.path != objectLocation.documentPath) {
                continue
            }

            val added = frame.add(objectLocation.objectPath, state)

            builder = builder.set(index, added)
        }

        if (builder == frames) {
            return this
        }
        return ImperativeModel(running, builder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findLast(
            objectLocation: ObjectLocation
    ): ImperativeFrame? {
        return frames.findLast {
            it.path == objectLocation.documentPath &&
                    it.contains(objectLocation.objectPath)
        }
    }


    fun containsStatus(status: ImperativePhase): Boolean {
        if (status == ImperativePhase.Running) {
            return isRunning()
        }

        for (frame in frames) {
            val frameRunning = running?.documentPath == frame.path

            if (frame.containsStatus(status, frameRunning)) {
                return true
            }
        }
        return false
    }


    fun isRunning(): Boolean {
//        return containsStatus(ImperativePhase.Running)
        return running != null
    }


    fun isActive(): Boolean {
        for (frame in frames) {
            if (frame.isActive(running)) {
                return true
            }

//            val frameRunning = running?.documentPath == frame.path
//
//            for (e in frame.states) {
//                if (frameRunning && e.key == running?.objectPath ||
//                        e.value.phase(false) != ImperativePhase.Pending) {
//                    return true
//                }
//            }
        }
        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(frames)
    }
}
