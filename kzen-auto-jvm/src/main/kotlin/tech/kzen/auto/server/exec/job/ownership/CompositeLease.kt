package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.control.ValueLease


/**
 * One lease over several: active while any member is, released together — every member is released even when
 * one of them fails to close, with the first close failure thrown and the rest suppressed.
 */
internal class CompositeLease(
    private val leases: List<ValueLease>
): ValueLease {
    override val isActive: Boolean
        get() = leases.any { it.isActive }


    override fun release() {
        ValueLeases.releaseAll(leases)
    }
}
