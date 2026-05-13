package tech.kzen.auto.plugin.model

import tech.kzen.auto.plugin.model.data.DataRecordBuffer


/**
 * Pre-allocated event slot in an LMAX Disruptor ring buffer.
 *
 * A producer obtains a slot via [tech.kzen.auto.plugin.api.managed.PipelineOutput.next],
 * mutates [data] (in place) and [endOfData], then calls
 * [tech.kzen.auto.plugin.api.managed.PipelineOutput.commit] to publish.
 * Slot ownership transfers to the consumer on commit; the producer must not
 * retain references to fields past `commit()`, and the consumer must not
 * retain references past handing the slot back to the ring.
 *
 * The `var` field is zero-copy by design — see [tech.kzen.auto.plugin.helper.DataFrameFeeder]
 * for the producer pattern. Disruptor sequence ordering guarantees single
 * producer / single consumer per slot; no synchronization needed on field
 * access.
 */
abstract class DataInputEvent {
    val data = DataRecordBuffer()
    var endOfData: Boolean = false
}