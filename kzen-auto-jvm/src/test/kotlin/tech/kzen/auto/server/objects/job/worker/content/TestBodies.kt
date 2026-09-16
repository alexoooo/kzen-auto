package tech.kzen.auto.server.objects.job.worker.content

import kotlinx.coroutines.awaitCancellation
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.content.scope.BodyEmitter
import tech.kzen.auto.server.objects.job.worker.content.scope.ScopeBodyWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CountDownLatch


/** Keeps the previous entry's [Content] and reads it on the next entry: must fail by name (design §6.2). */
@Reflect
class StashingBody: ScopeBodyWorker {
    companion object {
        @Volatile var stashed: Content? = null
        @Volatile var readFailure: Throwable? = null

        fun reset() {
            stashed = null
            readFailure = null
        }
    }

    override suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl) {
        val previous = stashed
        if (previous != null) {
            try {
                previous.open().use { it.read(ByteArray(8), 0, 8) }
            }
            catch (e: Throwable) {
                readFailure = e
                throw e
            }
        }
        stashed = (JobDataValues.native(element) as Entry).content
    }
}


/** Takes a named hold on the entry and never releases it: the scope must refuse to advance. */
@Reflect
class HoldingBody: ScopeBodyWorker {
    companion object {
        val leases = mutableListOf<ValueLease>()
        @Volatile var seen = 0

        fun reset() {
            leases.forEach { it.release() }
            leases.clear()
            seen = 0
        }
    }

    override suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl) {
        seen += 1
        leases.add(control.retain(element))
    }
}


@Reflect
class FailingBody: ScopeBodyWorker {
    override suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl) {
        throw IllegalStateException("injected body failure on '${(JobDataValues.native(element) as Entry).name}'")
    }
}


/** Reads part of the entry, signals, then suspends until cancelled — cancellation lands inside the entry. */
@Reflect
class BlockingBody: ScopeBodyWorker {
    companion object {
        @Volatile var entered = CountDownLatch(1)

        fun reset() {
            entered = CountDownLatch(1)
        }
    }

    override suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl) {
        val entry = JobDataValues.native(element) as Entry
        control.runBlockingIo {
            entry.content.open().read(ByteArray(16), 0, 16)
        }
        entered.countDown()
        awaitCancellation()
    }
}
