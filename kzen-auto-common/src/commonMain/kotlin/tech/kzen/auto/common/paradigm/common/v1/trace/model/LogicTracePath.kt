package tech.kzen.auto.common.paradigm.common.v1.trace.model

import tech.kzen.lib.common.model.locate.ObjectLocation


data class LogicTracePath(
    val segments: List<String>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val root = LogicTracePath(listOf())


        fun parse(asString: String): LogicTracePath {
            if (asString == "/") {
                return root
            }

            val segments = asString.split('/').drop(1)
            return LogicTracePath(segments)
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
        check(segments.none { it.contains('/') })
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return segments.joinToString("/", prefix = "/")
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