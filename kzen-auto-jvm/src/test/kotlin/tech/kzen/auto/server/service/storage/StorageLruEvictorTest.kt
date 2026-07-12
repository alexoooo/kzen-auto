package tech.kzen.auto.server.service.storage

import kotlin.test.Test
import kotlin.test.assertEquals


class StorageLruEvictorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private class FakeArea(
        override val budgetBytes: Long?,
        bundles: List<StorageBundle>
    ): ManagedStorageArea {
        override val id = "fake"
        override val displayName = "Fake"
        override val description = "In-memory"
        override val deletable = true

        val remaining = bundles.toMutableList()
        val deleted = mutableListOf<String>()

        override fun bundles(): List<StorageBundle> {
            return remaining.toList()
        }

        override fun deleteBundle(bundleKey: String): String? {
            val bundle = remaining.single { it.key == bundleKey }
            if (bundle.active) {
                return "In use: $bundleKey"
            }
            remaining.remove(bundle)
            deleted.add(bundleKey)
            return null
        }
    }


    private fun bundle(key: String, sizeBytes: Long, lastModifiedMillis: Long, active: Boolean = false): StorageBundle {
        return StorageBundle(key, key, sizeBytes, lastModifiedMillis, active)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun evictsOldestFirstUntilUnderBudget() {
        val area = FakeArea(budgetBytes = 100, bundles = listOf(
            bundle("newest", 60, lastModifiedMillis = 300),
            bundle("oldest", 60, lastModifiedMillis = 100),
            bundle("middle", 60, lastModifiedMillis = 200)))

        StorageLruEvictor(area).maybeEvict()

        assertEquals(listOf("oldest", "middle"), area.deleted)
        assertEquals(listOf("newest"), area.remaining.map { it.key })
    }


    @Test
    fun skipsActiveBundles() {
        val area = FakeArea(budgetBytes = 50, bundles = listOf(
            bundle("running", 60, lastModifiedMillis = 100, active = true),
            bundle("idle", 60, lastModifiedMillis = 200)))

        StorageLruEvictor(area).maybeEvict()

        assertEquals(listOf("idle"), area.deleted)
    }


    @Test
    fun underBudgetIsNoOp() {
        val area = FakeArea(budgetBytes = 1_000, bundles = listOf(
            bundle("a", 60, lastModifiedMillis = 100)))

        StorageLruEvictor(area).maybeEvict()

        assertEquals(listOf(), area.deleted)
    }


    @Test
    fun noBudgetIsNoOp() {
        val area = FakeArea(budgetBytes = null, bundles = listOf(
            bundle("a", 60, lastModifiedMillis = 100)))

        StorageLruEvictor(area).maybeEvict()

        assertEquals(listOf(), area.deleted)
    }
}
