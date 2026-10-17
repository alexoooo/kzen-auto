package tech.kzen.auto.server.objects.job.channel

import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.engine.resource.Hold


/**
 * The elements a [JobChannel] was holding at a migration barrier, in delivery order, each with the channel
 * hold it carried (null for an unowned element) — what [JobChannel.drainBuffered] hands to the run's capture
 * and [JobChannel.preload] seeds into the rebuilt channel. The holds move with the elements: a carried owned
 * element is still held by its channel (the holder is the channel's name, not its instance), so a migration
 * neither closes nor re-adopts anything. [release] is for a carryover no rebuilt channel adopts (the edit
 * removed the channel): the elements are dropped, so their holds are let go.
 */
class ChannelCarryover internal constructor(
    val elements: List<DataValue>,
    private val holds: List<Hold?>
) {
    companion object {
        val empty = ChannelCarryover(emptyList(), emptyList())

        /** A carryover of unowned elements (tests, and channels outside a run). */
        fun of(elements: List<DataValue>): ChannelCarryover =
            ChannelCarryover(elements, elements.map { null })
    }


    init {
        require(elements.size == holds.size) { "Holds must align with elements" }
    }


    val isEmpty: Boolean
        get() = elements.isEmpty()


    internal fun hold(index: Int): Hold? = holds[index]


    /** Lets go of every carried channel hold (the elements are dropped); the first close failure propagates. */
    fun release() {
        Hold.releaseAll(holds)
    }
}
