package tech.kzen.auto.server.objects.job.value.recycle

import java.util.concurrent.atomic.AtomicReference


/**
 * The free objects of one producing Worker instance. Releasers on any thread push onto an intrusive stack (the
 * object's own `next` link) with a CAS; the single consumer, the producer on its own coroutine, takes the whole
 * stack with one `getAndSet` into a private free list. Neither side allocates, and there is no ABA: there is one
 * consumer, and an object at zero holds cannot be pushed twice. A releaser's last read happens before its
 * decrement, which happens before its push, which happens before the producer's take.
 *
 * Empty, the pool creates; it never waits. A kept value is forfeited rather than held, so the pool never holds
 * more objects than were in flight at once, and needs no cap. A carried object released after its producer was
 * replaced (a live edit) returns to this pool, which is then unreachable and collected with it.
 */
class RecyclablePool<T: Recyclable>(
    private val create: (RecyclablePool<T>) -> T
) {
    private val returned = AtomicReference<Recyclable?>(null)

    // Confined to the producer
    private var free: Recyclable? = null

    /** How many objects this pool created: at most the peak number in flight. */
    @Volatile
    var created: Int = 0
        private set


    /**
     * An object with no holds, private to the caller until it is sent; one never sent is simply garbage. Called
     * only by the producer.
     */
    fun acquire(): T {
        val head = free
            ?: returned.getAndSet(null)
        if (head == null) {
            created += 1
            return create(this)
        }
        free = head.next
        head.next = null
        @Suppress("UNCHECKED_CAST")
        return head as T
    }


    internal fun recycle(item: Recyclable) {
        while (true) {
            val head = returned.get()
            item.next = head
            if (returned.compareAndSet(head, item)) {
                return
            }
        }
    }
}
