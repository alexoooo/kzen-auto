package tech.kzen.auto.server.exec

import tech.kzen.auto.common.paradigm.logic.RunTiming
import java.util.concurrent.TimeUnit

/** Controller-owned clock; finishing freezes duration without changing the engine's trace sequence. */
class RunElapsedTime(private val nanos: () -> Long = System::nanoTime) {
    private val started = nanos()
    private var finished: Long? = null

    fun finish() { if (finished == null) finished = nanos() }

    fun snapshot(runId: String): RunTiming = RunTiming(runId,
        TimeUnit.NANOSECONDS.toMillis((finished ?: nanos()) - started).coerceAtLeast(0), finished != null)
}
