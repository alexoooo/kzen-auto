package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.toPersistentList


data class ImperativeModel(
        val frames: PersistentList<ImperativeFrame>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun toCollection(model: ImperativeModel): List<Map<String, Any>> {
            return model
                    .frames
                    .map { ImperativeFrame.toCollection(it) }
        }


        fun fromCollection(
                collection: List<Map<String, Any>>
        ): ImperativeModel {
            return ImperativeModel(collection
                    .map { ImperativeFrame.fromCollection(it) }
                    .toPersistentList())
        }
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
        return ImperativeModel(builder)
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
        return ImperativeModel(builder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectLocation, newName: ObjectName): ImperativeModel {
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
        return ImperativeModel(builder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(objectLocation: ObjectLocation/*, indexInFrame: Int*/): ImperativeModel {
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
        return ImperativeModel(builder)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findLast(
            objectLocation: ObjectLocation
    ): ImperativeFrame? =
        frames.findLast {
            it.path == objectLocation.documentPath &&
                    it.contains(objectLocation.objectPath)
        }


    fun containsStatus(status: ImperativePhase): Boolean {
        for (frame in frames) {
            if (frame.states.values.find { it.phase() == status } != null) {
                return true
            }
        }
        return false
    }


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
