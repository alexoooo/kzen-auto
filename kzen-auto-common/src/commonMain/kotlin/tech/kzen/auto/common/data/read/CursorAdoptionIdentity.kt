package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest


@Serializable
data class CursorAdoptionIdentity(
    val partIdentity: Digest,
    val runPolicyIdentity: Digest
) {
    fun compatibleWith(other: CursorAdoptionIdentity): Boolean = this == other
}
