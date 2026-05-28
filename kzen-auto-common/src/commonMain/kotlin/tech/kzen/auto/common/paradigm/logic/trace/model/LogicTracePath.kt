package tech.kzen.auto.common.paradigm.logic.trace.model

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId


data class LogicTracePath(
    val segments: List<String>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val segmentSeparator = "/"


        // Marker prefix that distinguishes paths keyed by ObjectStableId (translated to the
        // current ObjectLocation at lookup time) from paths keyed by current location or
        // by fixed convention.
        const val stableIdMarker = "\$stable"


        val root = LogicTracePath(listOf())


        fun parse(asString: String): LogicTracePath {
            if (asString == segmentSeparator) {
                return root
            }

            val segments = asString.split(segmentSeparator).drop(1)
            return LogicTracePath(segments)
        }


        fun ofObjectStableId(objectStableId: ObjectStableId): LogicTracePath {
            // The id string is an ObjectLocation.asString(), which itself contains the
            // segment separator (document nesting, object nesting). Split so each piece
            // satisfies LogicTracePath's no-separator-in-segment invariant.
            return LogicTracePath(listOf(stableIdMarker) + objectStableId.value.split(segmentSeparator))
        }


        fun ofObjectLocation(objectLocation: ObjectLocation): LogicTracePath {
            val builder = mutableListOf<String>()

            val documentPath = objectLocation.documentPath
            builder.addAll(documentPath.nesting.segments.map { it.value })
            builder.add(documentPath.name.value)

            for (objectNestingSegment in objectLocation.objectPath.nesting.segments) {
                builder.add(objectNestingSegment.objectName.value)

                val attributePath = objectNestingSegment.attributePath
                builder.add(attributePath.attribute.value)
                builder.addAll(attributePath.nesting.segments.map { it.asString() })
            }

            builder.add(objectLocation.objectPath.name.value)

            val shortPath = builder
                .filter { it != "main" }

            return LogicTracePath(shortPath)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(segments.none { it.contains(segmentSeparator) }) {
            "Unexpected: $segments"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun objectStableId(): ObjectStableId? {
        if (segments.size < 2 || segments[0] != stableIdMarker) {
            return null
        }
        return ObjectStableId(segments.drop(1).joinToString(segmentSeparator))
    }


    fun append(segment: String): LogicTracePath {
        return LogicTracePath(segments + segment)
    }


    fun asString(): String {
        return segments.joinToString(segmentSeparator, prefix = segmentSeparator)
    }


    fun startsWith(prefix: LogicTracePath): Boolean {
        if (prefix.segments.isEmpty()) {
            return true
        }

        if (prefix.segments.size > segments.size) {
            return false
        }

        if (prefix.segments.size == segments.size) {
            return prefix.segments == segments
        }

        return prefix.segments == segments.subList(0, prefix.segments.size)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}