package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.toPersistentList


data class ExecutionModel(
        val frames: PersistentList<ExecutionFrame>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun toCollection(model: ExecutionModel): List<Map<String, Any>> {
            return model
                    .frames
                    .map { ExecutionFrame.toCollection(it) }
        }


        fun fromCollection(
                collection: List<Map<String, Any>>
        ): ExecutionModel {
            return ExecutionModel(collection
                    .map { ExecutionFrame.fromCollection(it) }
                    .toPersistentList())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun remove(documentPath: DocumentPath): Boolean {
//        var renamedAny = false
//    }

    fun remove(objectLocation: ObjectLocation): ExecutionModel {
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
        return ExecutionModel(builder)

//        val removed = frames.map {
//            if (it.path == objectLocation.documentPath) {
//                it.remove(objectLocation.objectPath)
//            }
//            else {
//                it
//            }
//        }.toPersistentList()
//
//        return (
//                if (frames == removed) {
//                    this
//                }
//                else {
//                    ExecutionModel(removed)
//                })

//        var anyChanged = false
//
//        for (frame in frames) {
//            if (frame.path != objectLocation.documentPath) {
//                continue
//            }
//
//            val changed = frame.remove(objectLocation.objectPath)
//
//            anyChanged = anyChanged || changed
//        }
//
//        return anyChanged
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectLocation, newName: ObjectName): ExecutionModel {
        var builder = frames
        for ((index, frame) in frames.withIndex()) {
            if (frame.path != from.documentPath) {
                continue
            }

            val renamed = frame.rename(from.objectPath, newName)
            builder = builder.set(index, renamed)
        }

        if (builder == frames) {
            return this
        }
        return ExecutionModel(builder)

//        var renamedAny = false
//
//        for (frame in frames) {
//            if (frame.path != from.documentPath) {
//                continue
//            }
//
//            val renamed = frame.rename(from.objectPath, newName)
//
//            renamedAny = renamedAny || renamed
//        }
//
//        return renamedAny
    }


    fun add(objectLocation: ObjectLocation/*, indexInFrame: Int*/): ExecutionModel {
        var builder = frames
        for ((index, frame) in frames.withIndex()) {
            if (frame.path != objectLocation.documentPath) {
                continue
            }

            val added = frame.add(objectLocation.objectPath/*, indexInFrame*/)
            builder = builder.set(index, added)
        }

        if (builder == frames) {
            return this
        }
        return ExecutionModel(builder)

//        for (frame in frames) {
//            if (frame.path != objectLocation.documentPath) {
//                continue
//            }
//
//            // TODO: just frame.add without checking contains?
//            if (! frame.contains(objectLocation.objectPath)) {
//                frame.add(objectLocation.objectPath, indexInFrame)
//            }
//            return true
//        }
//
//        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findLast(
            objectLocation: ObjectLocation
    ): ExecutionFrame? =
        frames.findLast {
            it.path == objectLocation.documentPath &&
                    it.contains(objectLocation.objectPath)
        }


    fun containsStatus(status: ExecutionPhase): Boolean {
        for (frame in frames) {
            if (frame.states.values.find { it.phase() == status } != null) {
                return true
            }
        }
        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun next(): ObjectLocation? {
//        if (frames.isEmpty() || containsStatus(ExecutionPhase.Running)) {
//            return null
//        }
//
//        val lastFrame = frames.last()
//
//        for (e in lastFrame.states) {
//            if (e.value.phase() == ExecutionPhase.Error) {
//                return null
//            }
//
//            if (e.value.phase() == ExecutionPhase.Pending) {
//                return ObjectLocation(lastFrame.path, e.key)
//            }
//        }
//
//        return null
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addInt(frames.size)

        for (frames in frames) {
            for (e in frames.states) {
                digest.addUtf8(e.key.asString())
                digest.addDigest(e.value.digest())
            }
        }

        return digest.digest()
    }
}
