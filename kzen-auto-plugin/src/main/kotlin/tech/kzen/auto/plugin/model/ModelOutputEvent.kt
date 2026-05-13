package tech.kzen.auto.plugin.model

import tech.kzen.auto.plugin.model.record.FlatFileRecord


/**
 * Pre-allocated event slot in an LMAX Disruptor ring buffer.
 *
 * A producer obtains a slot via [tech.kzen.auto.plugin.api.managed.PipelineOutput.next],
 * mutates [model] and [skip] (and the in-place buffer behind [row]), then
 * calls [tech.kzen.auto.plugin.api.managed.PipelineOutput.commit] to publish.
 * Slot ownership transfers to the consumer on commit; references to fields
 * must not be retained past the commit/release boundary.
 *
 * The `var` fields are zero-copy by design. Use [modelOrInit] to lazy-init
 * the model slot when a producer stage caches a heavy object across events.
 * Disruptor sequence ordering guarantees single producer / single consumer
 * per slot; no synchronization needed on field access.
 */
abstract class ModelOutputEvent<T> {
    var model: T? = null
    var skip: Boolean = false

    abstract val row: FlatFileRecord


    inline fun modelOrInit(factory: () -> T): T {
        if (model == null) {
            model = factory()
        }
        return model!!
    }
}