package tech.kzen.auto.common.objects.document.job.path


/** A validation error of one path (or a pair of paths, for a name collision) against the upstream contract. */
data class PathBindingError(
    val path: ProjectionPath,
    val message: String
) {
    override fun toString(): String = "${path.asString()}: $message"
}
