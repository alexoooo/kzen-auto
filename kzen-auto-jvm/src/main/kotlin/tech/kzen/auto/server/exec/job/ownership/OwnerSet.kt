package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.control.ValueLease


/**
 * The immutable set of owned natives a value's lifetime depends on: its own entry when it was adopted, plus
 * whatever its parent depended on (navigation and conservative expression outputs inherit — E9 item 3).
 * Almost always of size one. A lease on the set is a lease on every member, so a child in flight keeps its
 * parent open, and a member's own close never releases another member early.
 */
class OwnerSet private constructor(
    private val members: List<OwnedNative>
) {
    companion object {
        val empty = OwnerSet(emptyList())

        fun of(entry: OwnedNative): OwnerSet = OwnerSet(listOf(entry))
    }


    val isEmpty: Boolean
        get() = members.isEmpty()


    fun entries(): List<OwnedNative> = members


    operator fun contains(entry: OwnedNative): Boolean = members.any { it === entry }


    /** Union by identity, keeping this set's order first. */
    operator fun plus(other: OwnerSet): OwnerSet {
        if (other.isEmpty) return this
        if (isEmpty) return other
        val merged = members.toMutableList()
        for (entry in other.members) {
            if (merged.none { it === entry }) merged += entry
        }
        return OwnerSet(merged)
    }


    /**
     * One hold per member for [holder]; releasing the returned lease releases every member's hold once. Taken
     * in member order and, on a member that is already closed, the holds taken so far are released again and
     * the failure propagates — a value cannot be half-leased.
     */
    fun lease(holder: LeaseHolder): ValueLease {
        if (members.isEmpty()) {
            return ValueLease.none
        }
        val taken = mutableListOf<ValueLease>()
        try {
            for (entry in members) {
                taken += entry.lease(holder)
            }
        }
        catch (e: IllegalStateException) {
            taken.forEach { it.release() }
            throw e
        }
        return CompositeLease(taken)
    }


    override fun toString(): String = "OwnerSet($members)"
}
