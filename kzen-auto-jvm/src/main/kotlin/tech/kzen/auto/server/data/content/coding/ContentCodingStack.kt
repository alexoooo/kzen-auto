package tech.kzen.auto.server.data.content.coding

import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.server.data.content.ContentCodingException
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.lib.common.exec.MapExecutionValue

object ContentCodingStack {
    fun wrap(
        bytes: SequentialByteContent,
        codings: List<ContentCodingSpec>,
        control: ContentReadControl,
        source: String,
        part: String?
    ): SequentialByteContent {
        if (codings.size != 1) {
            throw ContentCodingException(source, part, 0, "Exactly one identity or gzip coding is required")
        }
        val coding = codings.single()
        val decoded = when (coding.identity) {
            ContentCodingSpec.identity.identity -> {
                requireEmptyConfig(coding, source, part)
                bytes
            }
            ContentCodingSpec.gzip.identity -> {
                requireEmptyConfig(coding, source, part)
                GzipSequentialByteContent(bytes, control, source, part)
            }
            else -> throw ContentCodingException(source, part, 0, "Unsupported coding '${coding.identity}'")
        }
        return ExpandedByteLimitContent(decoded, control, source, part)
    }


    private fun requireEmptyConfig(coding: ContentCodingSpec, source: String, part: String?) {
        if ((coding.config as? MapExecutionValue)?.values?.isEmpty() != true) {
            throw ContentCodingException(
                source, part, 0,
                "Content coding '${coding.identity}' requires an empty configuration map")
        }
    }
}
