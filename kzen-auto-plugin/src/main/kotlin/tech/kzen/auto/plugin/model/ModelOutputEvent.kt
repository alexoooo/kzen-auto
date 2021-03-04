package tech.kzen.auto.plugin.model


abstract class ModelOutputEvent<T> {
    var model: T? = null
    var skip: Boolean = false


    inline fun modelOrInit(factory: () -> T): T {
        if (model == null) {
            model = factory()
        }
        return model!!
    }
}