package tech.kzen.auto.server.exec.job

import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue


/**
 * Aggregates the components Job Workers yield ([tech.kzen.auto.common.paradigm.job.control.JobControl.yieldResult])
 * into the run's output [TupleValue] — owned per [JobRun] (a migrate rebuilds it empty; carried sinks re-yield at
 * their onComplete, which is why yield is last-write-wins). Synchronized: Workers yield concurrently from their own
 * engine nodes. First-yield order is the tuple's component order (a same-name overwrite keeps its position).
 */
class JobResultCollector {
    private val components = LinkedHashMap<TupleComponentName, Any?>()

    @Synchronized
    fun yieldResult(component: TupleComponentName, value: Any?) {
        components[component] = value
    }

    @Synchronized
    fun toTupleValue(): TupleValue {
        return TupleValue(components.map { TupleComponentValue(it.key, it.value) })
    }
}
