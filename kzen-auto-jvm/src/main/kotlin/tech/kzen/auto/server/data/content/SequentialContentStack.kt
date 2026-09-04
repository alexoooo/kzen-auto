package tech.kzen.auto.server.data.content

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.server.data.content.character.CharacterDecoder
import tech.kzen.auto.server.data.content.coding.ContentCodingStack
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.policy.ContentReadPolicy
import tech.kzen.auto.server.data.content.provider.DataContentDescriptor
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup


class SequentialContentStack(
    private val providers: DataContentProviderLookup
) {
    suspend fun openBytes(
        context: DataContext,
        ref: DataRef,
        expectedFingerprint: DataContentFingerprint?,
        codings: List<ContentCodingSpec>,
        required: ContentCapabilityIdentity,
        policy: ContentReadPolicy,
        part: String? = null
    ): OpenedReaderByteInput {
        val control = ContentReadControl(policy)
        control.beginOperation()
        try {
            val provider = providers.get(ref, part)
            val descriptor = provider.describe(context, ref, control)
            requireCapability(ref, part, descriptor, required)

            var owner: AutoCloseable? = null
            try {
                val handle = provider.acquire(context, ref, control)
                owner = handle
                control.checkpoint()
                requireCapability(ref, part, handle.descriptor, required)
                if (expectedFingerprint != null && expectedFingerprint != handle.observedFingerprint) {
                    throw ContentSourceException(
                        ref.display(), part,
                        "Fingerprint changed: expected ${expectedFingerprint.digest()}, " +
                            "observed ${handle.observedFingerprint.digest()}")
                }

                check(required == ContentCapabilityIdentity.sequentialBytes) {
                    "No consumer is implemented for content capability $required"
                }
                val decodedBytes = ContentCodingStack.wrap(handle.bytes, codings, control, ref.display(), part)
                owner = null
                return OpenedReaderByteInput(decodedBytes, control, handle.observedFingerprint)
            }
            catch (failure: Throwable) {
                closeSuppressing(owner, failure)
                throw failure
            }
        }
        finally {
            control.endOperation()
        }
    }


    suspend fun openCharacters(
        context: DataContext,
        ref: DataRef,
        expectedFingerprint: DataContentFingerprint?,
        codings: List<ContentCodingSpec>,
        characters: CharacterDecodingSpec,
        policy: ContentReadPolicy,
        part: String? = null
    ): SequentialCharacterContent {
        val control = ContentReadControl(policy)
        control.beginOperation()
        try {
            val provider = providers.get(ref, part)
            val descriptor = provider.describe(context, ref, control)
            requireCapability(ref, part, descriptor, ContentCapabilityIdentity.sequentialBytes)

            var owner: AutoCloseable? = null
            try {
                val handle = provider.acquire(context, ref, control)
                owner = handle
                control.checkpoint()
                requireCapability(ref, part, handle.descriptor, ContentCapabilityIdentity.sequentialBytes)
                if (expectedFingerprint != null && expectedFingerprint != handle.observedFingerprint) {
                    throw ContentSourceException(
                        ref.display(), part,
                        "Fingerprint changed: expected ${expectedFingerprint.digest()}, " +
                            "observed ${handle.observedFingerprint.digest()}")
                }

                val decodedBytes = ContentCodingStack.wrap(handle.bytes, codings, control, ref.display(), part)
                owner = decodedBytes
                val decodedCharacters = context.blocking {
                    CharacterDecoder.open(decodedBytes, characters, control, ref.display(), part)
                }
                owner = null
                return decodedCharacters
            }
            catch (t: Throwable) {
                closeSuppressing(owner, t)
                throw t
            }
        }
        finally {
            control.endOperation()
        }
    }


    private fun closeSuppressing(owner: AutoCloseable?, failure: Throwable) {
        try {
            owner?.close()
        }
        catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
    }


    private fun requireCapability(
        ref: DataRef,
        part: String?,
        descriptor: DataContentDescriptor,
        required: ContentCapabilityIdentity
    ) {
        if (required in descriptor.capabilities) return
        val available = descriptor.capabilities.joinToString { it.name }.ifEmpty { "none" }
        throw ContentSourceException(
            ref.display(), part,
            "Required capability '${required.name}' is unavailable; available capabilities: $available")
    }
}
