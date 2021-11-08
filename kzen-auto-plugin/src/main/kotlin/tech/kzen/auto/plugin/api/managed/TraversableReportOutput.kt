package tech.kzen.auto.plugin.api.managed

import tech.kzen.auto.plugin.model.ModelOutputEvent
import java.util.function.Consumer


interface TraversableReportOutput<T> {
    /**
     * @return true if has next
     */
    fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean
}