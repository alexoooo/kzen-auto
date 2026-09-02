package tech.kzen.auto.common.data.model

import kotlinx.serialization.Serializable
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable(with = DataPartSerializer::class)
data class DataPart(
    val role: DataRole,
    val ref: DataRef,
    val expectedFingerprint: DataContentFingerprint?,
    val resolvedRead: ResolvedReadSpec
): Digestible {
    companion object {
        fun ofExecutionValue(value: ExecutionValue): DataPart {
            val map = value.requiredModelMap("DataPart")
            return DataPart(
                DataRole(map.requiredText(DataModelKeys.role)),
                DataRef.ofExecutionValue(map.requiredMap(DataModelKeys.ref)),
                when (val fingerprint = map.requiredValue(DataModelKeys.expectedFingerprint)) {
                    NullExecutionValue -> null
                    is MapExecutionValue -> fingerprintOfExecutionValue(fingerprint)
                    else -> throw IllegalArgumentException(
                        "'${DataModelKeys.expectedFingerprint}' must be a map or null")
                },
                readOfExecutionValue(map.requiredMap(DataModelKeys.resolvedRead)))
        }

        private fun fingerprintOfExecutionValue(map: MapExecutionValue): DataContentFingerprint =
            DataContentFingerprint(
                map.requiredText("identity"),
                map.requiredValue("data"))

        private fun readOfExecutionValue(map: MapExecutionValue): ResolvedReadSpec {
            val reader = map.requiredMap("reader")
            val codings = map.requiredList("contentCodings").values.map { encoded ->
                val coding = encoded.requiredModelMap("ContentCodingSpec")
                tech.kzen.auto.common.data.read.ContentCodingSpec(
                    coding.requiredText("identity"),
                    coding.requiredValue("config"),
                    Digest.parse(coding.requiredText("configDigest")))
            }
            return ResolvedReadSpec(
                tech.kzen.auto.common.data.read.ReaderCapabilityIdentity(
                    reader.requiredText("namespace"),
                    reader.requiredText("name"),
                    reader.requiredText("compatibility")),
                codings,
                map.requiredValue("config"),
                Digest.parse(map.requiredText("configDigest")))
        }
    }

    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(linkedMapOf(
            DataModelKeys.role to TextExecutionValue(role.name),
            DataModelKeys.ref to ref.asExecutionValue(),
            DataModelKeys.expectedFingerprint to fingerprintExecutionValue(),
            DataModelKeys.resolvedRead to readExecutionValue()))
    }

    private fun fingerprintExecutionValue(): ExecutionValue = expectedFingerprint?.let {
        MapExecutionValue(mapOf(
            "identity" to TextExecutionValue(it.identity),
            "data" to it.data))
    } ?: NullExecutionValue

    private fun readExecutionValue(): MapExecutionValue = MapExecutionValue(mapOf(
        "reader" to MapExecutionValue(mapOf(
            "namespace" to TextExecutionValue(resolvedRead.reader.namespace),
            "name" to TextExecutionValue(resolvedRead.reader.name),
            "compatibility" to TextExecutionValue(resolvedRead.reader.compatibility))),
        "contentCodings" to tech.kzen.lib.common.exec.ListExecutionValue(
            resolvedRead.contentCodings.map { coding ->
                MapExecutionValue(mapOf(
                    "identity" to TextExecutionValue(coding.identity),
                    "config" to coding.config,
                    "configDigest" to TextExecutionValue(coding.configDigest.asString())))
            }),
        "config" to resolvedRead.config,
        "configDigest" to TextExecutionValue(resolvedRead.configDigest.asString())))

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(role.name)
        sink.addDigestible(ref)
        sink.addDigestibleNullable(expectedFingerprint)
        sink.addDigestible(resolvedRead)
    }
}
