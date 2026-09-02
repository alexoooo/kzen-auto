package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest


@Serializable
data class InspectionCacheIdentity(
    val partIdentity: Digest,
    val inspectionPolicyIdentity: Digest
)
