package tech.kzen.auto.common.objects.document.target


sealed class TargetSpec


data object FocusTarget: TargetSpec()


data class TextTarget(
    val text: String
): TargetSpec()


data class XpathTarget(
    val xpath: String
): TargetSpec()


data class VisualTarget(
    val document: TargetDocument
): TargetSpec()
