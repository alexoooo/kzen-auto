package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.InspectionCacheIdentity
import tech.kzen.auto.common.data.read.InspectionPolicy
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.lib.common.util.digest.Digest


data class SchemaCacheKey(
    val identity: InspectionCacheIdentity,
    // The code that computed the shape (see CodeFingerprint): same content under rebuilt code is a different entry
    val code: Digest
) {
    companion object {
        fun of(
            part: DataPart,
            inspectionPolicy: InspectionPolicy = InspectionPolicy(),
            code: Digest = CodeFingerprint.host
        ): SchemaCacheKey? {
            if (part.expectedFingerprint == null) {
                return null
            }
            return SchemaCacheKey(
                InspectionCacheIdentity(part.digest(), inspectionPolicy.digest()),
                code)
        }


        fun ofReport(
            ref: DataRef,
            format: CommonPluginCoordinate,
            encoding: CommonDataEncodingSpec,
            code: Digest = CodeFingerprint.host
        ): SchemaCacheKey? {
            val fingerprint = DataContentFingerprint.localOrNull(ref) ?: return null
            val reportReadIdentity = Digest.build {
                addDigestible(ref)
                addDigestible(fingerprint)
                addUtf8(format.asString())
                addUtf8(encoding.asString())
            }
            return SchemaCacheKey(
                InspectionCacheIdentity(reportReadIdentity, InspectionPolicy().digest()),
                code)
        }
    }

    fun digest(): Digest = Digest.build {
        addDigest(identity.partIdentity)
        addDigest(identity.inspectionPolicyIdentity)
        addDigest(code)
    }
}
