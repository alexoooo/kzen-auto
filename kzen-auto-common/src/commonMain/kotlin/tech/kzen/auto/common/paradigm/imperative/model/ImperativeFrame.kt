package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class ImperativeFrame(
        val path: DocumentPath,
        val states: PersistentMap<ObjectPath, ImperativeState>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val pathKey = "path"
        private const val statesKey = "states"


        fun toCollection(frame: ImperativeFrame): Map<String, Any> {
            val builder = mutableMapOf<String, Any>()

            builder[pathKey] = frame.path.asRelativeFile()

            val values = mutableMapOf<String, Map<String, Any?>>()
            for (e in frame.states) {
                values[e.key.asString()] = ImperativeState.toCollection(e.value)
            }
            builder[statesKey] = values

            return builder
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
                collection: Map<String, Any>
        ): ImperativeFrame {
            val relativeLocation = collection[pathKey] as String
            val path = DocumentPath.parse(relativeLocation)

            val valuesMap = collection[statesKey] as Map<*, *>

            val values = mutableMapOf<ObjectPath, ImperativeState>()
            for (e in valuesMap) {
                values[ObjectPath.parse(e.key as String)] =
                        ImperativeState.fromCollection(e.value as Map<String, Any?>)
            }

            return ImperativeFrame(path, values.toPersistentMap())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(objectPath: ObjectPath): Boolean {
        return states.containsKey(objectPath)
    }


    fun containsStatus(status: ImperativePhase, running: Boolean): Boolean {
        for (e in states) {
            if (e.value.phase(running) != status) {
                return true
            }
        }
        return false
    }


    fun isActive(running: ObjectLocation?): Boolean {
        val frameRunning = running?.documentPath == path

        for (e in states) {
            if (frameRunning && e.key == running?.objectPath ||
                    e.value.phase(false) != ImperativePhase.Pending) {
                return true
            }
        }

        return false
    }


    fun set(objectPath: ObjectPath, executionState: ImperativeState): ImperativeFrame {
        return copy(states = states.put(objectPath, executionState))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectPath, newObjectPath: ObjectPath): ImperativeFrame {
        val state = states[from]
                ?: return this

        val removedAtOldName = states.remove(from)
        val addedAtNewNesting = removedAtOldName.put(newObjectPath, state)

        return copy(states = addedAtNewNesting)
    }


    fun add(objectPath: ObjectPath, state: ImperativeState): ImperativeFrame {
        check(objectPath !in states) { "Already present: $objectPath" }

        return copy(states = states.put(
                objectPath, state))
    }


    fun remove(objectPath: ObjectPath): ImperativeFrame {
        return copy(states = states.remove(objectPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(): Digest {
        val digest = Digest.Builder()
        digest(digest)
        return digest.digest()
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(path)
        sink.addDigestibleUnorderedMap(states)
    }
}
