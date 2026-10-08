package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.auto.common.paradigm.job.control.ValueLease
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import java.util.concurrent.atomic.AtomicInteger


/**
 * A pooled object a Job value reads through (its [RecyclableAccess]), returned to its producer's [RecyclablePool]
 * once nothing can read it again. Its whole state is one atomic: the hold count and a forfeited bit. Each channel
 * slot that carries the object takes one [hold] and carries [token]; releasing the token lets that hold go, and
 * the last release returns the object to its pool. A place where a value may outlive its callback inside code the
 * framework cannot see (`JobControl.retain` / `snapshot`, a Logic boundary, an expression's derived output, a raw
 * channel read) calls [forfeit] instead: from then on the object is an ordinary value the GC reclaims.
 *
 * The rule behind every choice: a pooled object may leak, but must never be recycled early. A leak costs one
 * allocation; an early recycle is silent corruption.
 *
 * Deliberately not `AutoCloseable`, so the run's ownership ledger and lending never adopt it. A pooled object owns
 * its storage and is never a view over another value, so it never has ledger owners and the two mechanisms stay
 * disjoint.
 */
abstract class Recyclable(
    private val pool: RecyclablePool<*>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val forfeitedBit = 1 shl 30
        private const val holdMask = forfeitedBit - 1


        /** The pooled object behind [value], or null for an ordinary value: one interface check. */
        fun of(value: DataValue): Recyclable? =
            (value.access as? RecyclableAccess)?.recyclable


        /** Forfeits the pooled object behind [value], if any. */
        fun forfeit(value: DataValue) {
            of(value)?.forfeit()
        }


        /**
         * Forfeits the pooled object behind [input] when [output] was computed from it as a value of its own, which
         * may have returned or embedded the input. An output that shares the input's access (new metadata only)
         * is held by its own send, and a scalar embeds nothing.
         */
        fun forfeitDerived(output: DataValue, input: DataValue) {
            val recyclable = of(input)
                ?: return
            if (output.access !== input.access && output.type !is DataType.Scalar) {
                recyclable.forfeit()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The hold a channel slot carries, preallocated and shared by every slot that carries this object: each
     * release lets go of exactly one [hold]. Unlike other leases a second release is NOT a no-op, so every slot
     * must be released at most once (an extra release that reaches below zero throws by name).
     */
    inner class Token: ValueLease {
        val recyclable: Recyclable
            get() = this@Recyclable

        override val isActive: Boolean
            get() = holds() > 0

        override fun release() {
            this@Recyclable.release()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val state = AtomicInteger(0)

    // The link of the pool's intrusive return stack: written by a releaser before its push, read by the producer
    // after it took the stack (see RecyclablePool)
    internal var next: Recyclable? = null

    val token: Token = Token()

    /** Bumped every time the object returns to its pool; tests compare it on read to catch use after recycle. */
    @Volatile
    var generation: Int = 0
        private set


    //-----------------------------------------------------------------------------------------------------------------
    fun hold() {
        state.incrementAndGet()
    }


    /** Lets go of one [hold]; the last one returns the object to its pool, unless it was forfeited. */
    fun release() {
        val after = state.decrementAndGet()
        if ((after and holdMask) == holdMask) {
            throw IllegalStateException("${javaClass.name} was released more times than it was held")
        }
        if (after == 0) {
            generation += 1
            pool.recycle(this)
        }
    }


    /** From here on the object never returns to a pool. Idempotent. */
    fun forfeit() {
        state.getAndUpdate { it or forfeitedBit }
    }


    val isForfeited: Boolean
        get() = (state.get() and forfeitedBit) != 0


    fun holds(): Int =
        state.get() and holdMask
}
