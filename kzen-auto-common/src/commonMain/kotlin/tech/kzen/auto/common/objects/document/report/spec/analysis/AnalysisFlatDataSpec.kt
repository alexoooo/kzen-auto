package tech.kzen.auto.common.objects.document.report.spec.analysis

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class AnalysisFlatDataSpec(
    val allowPatterns: List<String>,
    val excludePatterns: List<String>
): Digestible {
    companion object {
        val empty = AnalysisFlatDataSpec(listOf(), listOf())

        private const val allowKey = "allow"
        private val allowSegment = AttributeSegment.ofKey(allowKey)

        private const val excludeKey = "exclude"
        private val excludeSegment = AttributeSegment.ofKey(excludeKey)


        fun ofNotation(attributeNotation: MapAttributeNotation): AnalysisFlatDataSpec {
            val allowNotation = attributeNotation.get(allowSegment) as ListAttributeNotation
            val excludeNotation = attributeNotation.get(excludeSegment) as ListAttributeNotation

            val allowPatterns = allowNotation.values.map { (it as ScalarAttributeNotation).value }
            val excludePatterns = excludeNotation.values.map { (it as ScalarAttributeNotation).value }

            return AnalysisFlatDataSpec(allowPatterns, excludePatterns)
        }
    }


    override fun digest(sink: Digest.Sink) {
        sink.addCollection(allowPatterns) { addUtf8(it) }
        sink.addCollection(excludePatterns) { addUtf8(it) }
    }
}