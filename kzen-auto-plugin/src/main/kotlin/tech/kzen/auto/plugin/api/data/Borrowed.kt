package tech.kzen.auto.plugin.api.data


/**
 * Declares that [value] is **not** the run's to close: a host object shared beyond one run (a connection, a
 * repository, a cached model) or a closeable *child* whose parent's own `close()` already cascades to it. An
 * `AutoCloseable` a Worker or expression emits is otherwise adopted by the run and closed when the last
 * holder lets go; wrapping it in `Borrowed` suppresses that independent adoption while any ownership the
 * value inherited from its parent is preserved. An ownership declaration only — nothing here inspects or
 * restricts the object.
 */
class Borrowed<T: Any> private constructor(
    val value: T
) {
    companion object {
        @JvmStatic
        fun <T: Any> of(value: T): Borrowed<T> {
            return Borrowed(value)
        }
    }

    override fun toString(): String = "Borrowed($value)"
}
