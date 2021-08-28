package tech.kzen.auto.common.paradigm.common.v1.trace.model


data class LogicTracePath(
    val segments: List<String>
) {
    companion object {
        val root = LogicTracePath(listOf())

        fun parse(asString: String): LogicTracePath {
            if (asString == "/") {
                return root
            }

            val segments = asString.split('/').drop(1)
            return LogicTracePath(segments)
        }
    }


    init {
        check(segments.none { it.contains('/') })
    }


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


    override fun toString(): String {
        return asString()
    }
}