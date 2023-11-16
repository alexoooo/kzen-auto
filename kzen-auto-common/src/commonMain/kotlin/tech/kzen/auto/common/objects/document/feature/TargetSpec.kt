package tech.kzen.auto.common.objects.document.feature


sealed class TargetSpec


data object FocusTarget: TargetSpec()


data class TextTarget(
        val text: String
): TargetSpec()


data class XpathTarget(
        val xpath: String
): TargetSpec()


data class VisualTarget(
        val feature: FeatureDocument
): TargetSpec()
