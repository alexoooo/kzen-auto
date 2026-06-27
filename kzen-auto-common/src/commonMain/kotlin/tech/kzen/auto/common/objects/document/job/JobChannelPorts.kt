package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * Single source of truth for the four Job channel-endpoint port types and their data-flow direction.
 *
 * A Worker declares channel ports as arbitrarily-named attributes whose TYPE is one of these (the attribute
 * value is a reference to the shared Channel object). [tech.kzen.auto.server.objects.job.JobChannelCreator]
 * dispatches the injected endpoint view on that type, and
 * [tech.kzen.auto.common.objects.document.job.ChannelTypeDefiner] validates wiring by it. This object lets the
 * order-driven channel synthesis ([JobChannelDerivation] / [JobChannelSynthesis]) and the JS editor classify a
 * port from its metadata type without re-spelling the api class-name strings (ChannelTypeDefiner keeps its own
 * copy of these constants for its hot validation loop — keep the two in sync).
 */
object JobChannelPorts {
    //-----------------------------------------------------------------------------------------------------------------
    enum class Direction {
        // Drains the channel — the single-reader side (ChannelInput over a one-way Channel, ChannelServer over
        // a duplex DuplexChannel).
        Consumer,

        // Feeds the channel (ChannelOutput over a one-way Channel, ChannelClient over a duplex DuplexChannel).
        Producer
    }


    enum class Kind(
        val className: String,
        val direction: Direction,
        val duplex: Boolean
    ) {
        Input("tech.kzen.auto.common.paradigm.job.api.ChannelInput", Direction.Consumer, false),
        Output("tech.kzen.auto.common.paradigm.job.api.ChannelOutput", Direction.Producer, false),
        Server("tech.kzen.auto.common.paradigm.job.api.ChannelServer", Direction.Consumer, true),
        Client("tech.kzen.auto.common.paradigm.job.api.ChannelClient", Direction.Producer, true);

        companion object {
            private val byClassName = entries.associateBy { it.className }

            fun ofClassName(className: String): Kind? {
                return byClassName[className]
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The port Kind a Worker attribute's metadata type denotes, or null when the attribute is not a channel port.
    fun kindOf(type: TypeMetadata?): Kind? {
        val className = type?.className?.asString()
            ?: return null
        return Kind.ofClassName(className)
    }


    fun isChannelPort(type: TypeMetadata?): Boolean {
        return kindOf(type) != null
    }
}
