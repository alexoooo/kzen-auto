package tech.kzen.auto.server.data.write

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.server.data.write.delimited.ConfiguredDelimitedWriterCapability


class RecordWriterCapabilityRegistry(
    capabilities: Iterable<RecordWriterCapability>
) {
    companion object {
        fun withBuiltInWriters(): RecordWriterCapabilityRegistry =
            RecordWriterCapabilityRegistry(listOf(ConfiguredDelimitedWriterCapability))
    }

    private val capabilities = linkedMapOf<String, RecordWriterCapability>()

    init {
        for (capability in capabilities) {
            require(capability.identity.isNotBlank()) { "Record writer identity must not be blank" }
            val previous = this.capabilities.put(capability.identity, capability)
            check(previous == null) {
                "Duplicate record writer ${capability.identity}: " +
                    "${previous!!::class.java.name} and ${capability::class.java.name}"
            }
        }
    }


    /** The writer of [format]; null when it declares none, or names one that is not registered. */
    fun writerFor(format: ConfiguredRecordFormat): RecordWriterCapability? =
        format.writerCapabilityIdentity?.let(capabilities::get)
}
