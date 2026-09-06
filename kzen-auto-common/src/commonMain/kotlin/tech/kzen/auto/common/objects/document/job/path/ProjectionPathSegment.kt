package tech.kzen.auto.common.objects.document.job.path


/**
 * One step of a [ProjectionPath]: a named field (`executions`, and after a map's wildcard the entry's `key` /
 * `value`), or the unnesting wildcard `[*]` over a list or map.
 */
sealed interface ProjectionPathSegment {
    data class Field(val name: String): ProjectionPathSegment {
        override fun asString(): String = name
    }

    data object Wildcard: ProjectionPathSegment {
        const val text = "[*]"

        override fun asString(): String = text
    }


    fun asString(): String
}
