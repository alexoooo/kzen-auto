package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.lib.common.exec.engine.resource.Holder
import tech.kzen.lib.common.exec.engine.resource.Resource
import tech.kzen.lib.common.exec.engine.resource.ResourceOwners


/**
 * A pooled object a Job value reads through (its [RecyclableAccess]), returned to its producer's [RecyclablePool]
 * once nothing can read it again. It is an ordinary run [Resource] whose close returns the object to its pool: a
 * value over it carries that resource as its [owners], so every channel slot, callback and retention that holds
 * the value holds the object, and the last release recycles it. Each trip out of the pool is a new resource —
 * a closed resource refuses holds, which is exactly the "never read after recycle" rule.
 *
 * A place where a value may outlive its callback inside code the framework cannot see (a Logic boundary, plain
 * Java code) [forfeit]s the object instead: a hold nobody ever releases, so the object never returns to a pool
 * and the GC reclaims it with whoever kept it. The rule behind every choice: a pooled object may leak, but must
 * never be recycled early. A leak costs one allocation; an early recycle is silent corruption.
 */
abstract class Recyclable(
    private val pool: RecyclablePool<*>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val forfeitHolder = Holder("forfeited")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The link of the pool's intrusive return stack: written by a releaser before its push, read by the producer
    // after it took the stack (see RecyclablePool)
    internal var next: Recyclable? = null

    /** Bumped every time the object returns to its pool; tests compare it on read to catch use after recycle. */
    @Volatile
    var generation: Int = 0
        private set

    // Bound once: every trip's resource closes through the same reference
    private val recycler: () -> Unit = ::recycle

    /** The resource standing for this trip out of the pool: closing it is the return. */
    @Volatile
    var resource: Resource = issue()
        private set

    /** What a value over this object carries as its owners: the current [resource], alone. */
    @Volatile
    var owners: ResourceOwners = ResourceOwners.of(resource)
        private set


    //-----------------------------------------------------------------------------------------------------------------
    /** A fresh resource for the next trip out of the pool; called by the pool as it hands the object out again. */
    internal fun reissue() {
        resource = issue()
        owners = ResourceOwners.of(resource)
    }


    private fun issue(): Resource =
        Resource(this, recycler)


    private fun recycle() {
        generation += 1
        pool.recycle(this)
    }


    /** From here on the object never returns to a pool: a hold that is never released. Idempotent in effect. */
    fun forfeit() {
        resource.hold(forfeitHolder)
    }


    val isForfeited: Boolean
        get() = resource.holds().containsKey(forfeitHolder)


    fun holds(): Int =
        resource.holdCount()
}
