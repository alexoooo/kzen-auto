package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/**
 * A path over an incoming record contract (E8): dotted field names with an optional `[*]` after a field that
 * is a list or map — `instrument.symbol`, `executions[*].price`, `attributes[*].value.price`. `[*]` unnests:
 * one output row per element, and after a map's `[*]` the entry exposes `key` and `value`. A path starts in the
 * value's payload; a leading `meta` starts it in the value's metadata instead (`meta.parent.name`), and a leading
 * `this` names the payload explicitly. A bare path whose first name is not the payload's but is the metadata's
 * resolves into the metadata. The default output name is the full path joined with `.` with the wildcards and a
 * leading `meta` / `this` dropped (`executions[*].trade.venue` → `executions.trade.venue`); an alias on the entry
 * overrides it. Parsing is syntactic only — binding against a contract ([PathBinding]) is where a name is
 * checked to exist and a leaf to be scalar.
 */
data class ProjectionPath(
    val segments: List<ProjectionPathSegment>
): Digestible {
    companion object {
        private const val separator = '.'
        private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** Parses `a.b[*].c`; a malformed path is an [IllegalArgumentException] naming the offending piece. */
        fun parse(text: String): ProjectionPath {
            require(text.isNotBlank()) { "Path must not be blank" }
            val segments = ArrayList<ProjectionPathSegment>()
            for (piece in text.split(separator)) {
                var name = piece
                var wildcard = false
                if (name.endsWith(ProjectionPathSegment.Wildcard.text)) {
                    wildcard = true
                    name = name.removeSuffix(ProjectionPathSegment.Wildcard.text)
                }
                require(identifier.matches(name)) { "Invalid path segment '$piece' in '$text'" }
                segments.add(ProjectionPathSegment.Field(name))
                if (wildcard) {
                    segments.add(ProjectionPathSegment.Wildcard)
                }
            }
            return ProjectionPath(segments)
        }


        /** The facet a path names explicitly by its first segment (`meta.` or `this.`), or null for a bare path. */
        fun facetOf(path: ProjectionPath): Facet? {
            if (path.segments.size < 2) {
                return null
            }
            return when ((path.segments.first() as ProjectionPathSegment.Field).name) {
                Facet.Metadata.qualifier -> Facet.Metadata
                Facet.Payload.qualifier -> Facet.Payload
                else -> null
            }
        }
    }


    /** Which part of a value a path starts in: its payload (`this`) or its metadata (`meta`). */
    enum class Facet(val qualifier: String) {
        Payload("this"),
        Metadata("meta")
    }


    init {
        require(segments.isNotEmpty()) { "Path must have at least one segment" }
        require(segments.first() is ProjectionPathSegment.Field) { "Path must start with a field" }
    }


    val unnests: Boolean
        get() = segments.any { it is ProjectionPathSegment.Wildcard }


    fun asString(): String {
        val text = StringBuilder()
        for (segment in segments) {
            if (segment is ProjectionPathSegment.Field && text.isNotEmpty()) {
                text.append(separator)
            }
            text.append(segment.asString())
        }
        return text.toString()
    }


    /** The output-name convention: the field names joined with `.`, wildcards and a leading `meta` / `this` dropped. */
    fun defaultOutputName(): String {
        val names = segments.filterIsInstance<ProjectionPathSegment.Field>().map { it.name }
        val unqualified = if (facetOf(this) != null && names.size > 1) names.drop(1) else names
        return unqualified.joinToString(separator.toString())
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(asString())
    }


    override fun toString(): String = asString()
}
