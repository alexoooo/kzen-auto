package tech.kzen.auto.common.objects.document.target


/**
 * How multiple candidate matches of a target resolve, uniform across target types
 * (`policy:` key on the `target:` notation map, default [Unique]):
 *
 * - [Unique] — exactly one candidate may match; more is an error (the RPA-safe default:
 *   an ambiguous target fails loudly rather than acting on the wrong element).
 * - [First] — the first candidate in the type's natural order.
 * - [Nth] — the candidate at [Nth.index] (zero-based, `index:` key).
 * - [Best] — the highest-scoring candidate for score-ranked types (visual); types without
 *   scores treat it as [First].
 */
sealed class TargetMatchPolicy {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val policyKey = "policy"
        const val indexKey = "index"

        private const val uniqueName = "unique"
        private const val firstName = "first"
        private const val nthName = "nth"
        private const val bestName = "best"


        /** null [policyName] is [Unique]; an unknown name (or nth without an index) is null. */
        fun parse(policyName: String?, index: Int?): TargetMatchPolicy? {
            return when (policyName) {
                null, uniqueName -> Unique
                firstName -> First
                nthName -> index?.let { Nth(it) }
                bestName -> Best
                else -> null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    data object Unique: TargetMatchPolicy()

    data object First: TargetMatchPolicy()

    data class Nth(
        val index: Int
    ): TargetMatchPolicy()

    data object Best: TargetMatchPolicy()
}
