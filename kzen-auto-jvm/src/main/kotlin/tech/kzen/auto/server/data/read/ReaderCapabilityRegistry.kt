package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.FormatAuthoringCapability
import tech.kzen.auto.plugin.api.data.ReaderProbeCapability
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.data.read.text.PlainTextReaderCapability
import java.util.ServiceLoader


class ReaderCapabilityRegistry(
    capabilities: Iterable<ReaderCapability>
) {
    companion object {
        fun withConfiguredReaders(
            classLoader: ClassLoader = activeContextClassLoader()
        ): ReaderCapabilityRegistry = ReaderCapabilityRegistry(buildList {
            add(ConfiguredDelimitedReaderCapability)
            add(PlainTextReaderCapability)
            ServiceLoader.load(ReaderCapability::class.java, classLoader).forEach { add(it) }
        })

        fun withBuiltInReaders(): ReaderCapabilityRegistry = withConfiguredReaders()

        private fun activeContextClassLoader(): ClassLoader =
            Thread.currentThread().contextClassLoader
                ?: ReaderCapabilityRegistry::class.java.classLoader
    }

    private val capabilities = linkedMapOf<ReaderCapabilityIdentity, ReaderCapability>()
    private val probes = linkedMapOf<String, ReaderProbeCapability>()
    private val authoringCapabilities = linkedMapOf<String, FormatAuthoringCapability>()

    init {
        for (capability in capabilities) {
            val previous = this.capabilities.put(capability.identity, capability)
            check(previous == null) {
                "Duplicate reader capability ${capability.identity}: " +
                    "${previous!!::class.java.name} and ${capability::class.java.name}"
            }
            if (capability is ReaderProbeCapability) {
                require(capability.readerCompatibility == capability.identity.compatibility) {
                    "Reader probe ${capability::class.java.name} declares compatibility " +
                        "${capability.readerCompatibility}, but its reader declares ${capability.identity.compatibility}"
                }
                val previousProbe = probes.put(capability.readerCompatibility, capability)
                check(previousProbe == null) {
                    "Duplicate reader probe capability ${capability.readerCompatibility}: " +
                        "${previousProbe!!::class.java.name} and ${capability::class.java.name}"
                }
            }
            if (capability is FormatAuthoringCapability) {
                require(capability.authoringIdentity.isNotBlank()) { "Format-authoring identity must not be blank" }
                val previousAuthoring = authoringCapabilities.put(capability.authoringIdentity, capability)
                check(previousAuthoring == null) {
                    "Duplicate format-authoring capability ${capability.authoringIdentity}: " +
                        "${previousAuthoring!!::class.java.name} and ${capability::class.java.name}"
                }
            }
        }
    }

    fun resolve(identity: ReaderCapabilityIdentity): ReaderCapability =
        capabilities[identity]
            ?: throw IllegalArgumentException("Unknown reader capability: $identity")


    fun probeFor(identity: ReaderCapabilityIdentity): ReaderProbeCapability? = probes[identity.compatibility]


    fun authoringFor(identity: String): FormatAuthoringCapability? = authoringCapabilities[identity]


    fun probeCompatibilityIdentities(): Set<String> = probes.keys

    fun decodeValidateCanonicalize(spec: ResolvedReadSpec): ReaderConfig {
        val capability = resolve(spec.reader)
        val decoded = capability.decode(spec.config)
        capability.validate(decoded)
        val canonical = capability.canonicalize(decoded)
        capability.validate(canonical)
        val encoded = capability.encode(canonical)
        require(encoded.digest() == spec.configDigest) {
            "Reader config is not canonical for ${spec.reader}"
        }
        return canonical
    }
}
