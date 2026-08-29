package tech.kzen.auto.server.exec.job

import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.binding.ProducedBindingsBuilder
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * Aggregates the components Job Workers yield ([tech.kzen.auto.common.paradigm.job.control.JobControl.yieldResult])
 * into the run's output [DataBindings] — owned per [JobRun] (a migrate rebuilds it empty; carried sinks re-yield at
 * their onComplete, which is why yield is last-write-wins). Synchronized: Workers yield concurrently from their own
 * engine nodes. First-yield order is the binding order (a same-name overwrite keeps its position).
 */
class JobResultCollector(schema: BindingSchema) {
    private val produced = ProducedBindingsBuilder(schema)

    fun yieldResult(component: BindingName, value: DataValue) {
        produced.set(component, value)
    }

    fun settle(): DataBindings = produced.settle()
}
