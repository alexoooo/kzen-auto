package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.exec.data.type.DataContract


/**
 * The outcome of binding a [PathProjectionSpec] against an upstream contract: the bound paths in entry order
 * and the flat output [contract] when every path bound, or the [errors] (all of them, so an editor can show
 * each path's problem at once).
 */
data class PathBindingResult(
    val paths: List<BoundPath>,
    val contract: DataContract?,
    val errors: List<PathBindingError>
) {
    val isValid: Boolean
        get() = errors.isEmpty() && contract != null


    fun errorMessage(): String? =
        errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
}
