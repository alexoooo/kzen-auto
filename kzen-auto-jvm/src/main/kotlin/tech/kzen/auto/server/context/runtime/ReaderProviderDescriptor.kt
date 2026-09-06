package tech.kzen.auto.server.context.runtime

import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.plugin.api.data.ReaderCapability
import java.util.function.Supplier


/**
 * A `ServiceLoader`-discovered reader provider, held by the runtime as a descriptor: its declaring scope, the
 * provider class, the identity read once at boot (for the global duplicate check), and a supplier each context
 * uses to instantiate its own capability instance. Capabilities are therefore cheap to construct by contract.
 */
class ReaderProviderDescriptor(
    val scopeId: PluginScopeId,
    val providerClass: Class<out ReaderCapability>,
    val identity: ReaderCapabilityIdentity,
    private val supplier: Supplier<out ReaderCapability>
) {
    fun instantiate(): ReaderCapability {
        return supplier.get()
    }

    override fun toString(): String {
        return "${providerClass.name} ($identity) from scope '$scopeId'"
    }
}
