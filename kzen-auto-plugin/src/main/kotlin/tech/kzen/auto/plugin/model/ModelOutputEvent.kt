package tech.kzen.auto.plugin.model

import tech.kzen.auto.plugin.api.managed.FlatRecordBuilder


abstract class ModelOutputEvent<T> {
    var model: T? = null
    var skip: Boolean = false

    abstract val row: FlatRecordBuilder


    inline fun modelOrInit(factory: () -> T): T {
        if (model == null) {
            model = factory()
        }
        return model!!
    }
}