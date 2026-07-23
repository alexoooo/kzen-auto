package tech.kzen.auto.common.objects.document.logic

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.util.digest.Digest


/**
 * The digest handshake between a server-side document validator and its client store: the validator echoes
 * (in ExecutionSuccess.detail) the digest of the host DocumentNotation its result was computed against, and
 * the client applies the result only when the echo matches its current local notation.
 *
 * Required because a commit's local apply publishes — triggering the validation fetch — before the remote
 * write lands (MirroredGraphStore applies local and remote concurrently), so a fetch can be served from
 * pre-commit server notation; and because overlapping detached responses can arrive out of order.
 *
 * An absent echo (Null detail) means a validator that does not take part: apply unconditionally.
 */
object ValidationDigestEcho {
    fun detail(documentNotation: DocumentNotation): ExecutionValue {
        return TextExecutionValue(documentNotation.digest().asString())
    }


    fun ofDetail(detail: ExecutionValue): Digest? {
        return (detail as? TextExecutionValue)?.let { Digest.parse(it.value) }
    }
}
