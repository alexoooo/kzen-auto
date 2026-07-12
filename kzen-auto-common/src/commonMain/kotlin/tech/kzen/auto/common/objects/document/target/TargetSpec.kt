package tech.kzen.auto.common.objects.document.target


/**
 * A runtime action target. Open set: each type is created by its registered [TargetSpecType]
 * (see there for how types register), and located server-side by its TargetTypeLocator.
 */
interface TargetSpec {
    /** How multiple candidate matches resolve; [TargetMatchPolicy.Unique] is the RPA-safe default. */
    val policy: TargetMatchPolicy get() = TargetMatchPolicy.Unique
}


/** The element that currently has focus (inherently single; policy does not apply). */
data object FocusTarget: TargetSpec


data class TextTarget(
    val text: String,
    override val policy: TargetMatchPolicy = TargetMatchPolicy.Unique
): TargetSpec


data class XpathTarget(
    val xpath: String,
    override val policy: TargetMatchPolicy = TargetMatchPolicy.Unique
): TargetSpec


data class VisualTarget(
    val document: TargetDocument,
    override val policy: TargetMatchPolicy = TargetMatchPolicy.Unique
): TargetSpec
