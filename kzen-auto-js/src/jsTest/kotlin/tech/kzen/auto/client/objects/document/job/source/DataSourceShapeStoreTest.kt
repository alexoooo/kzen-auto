package tech.kzen.auto.client.objects.document.job.source

import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class DataSourceShapeStoreTest {
    private val source = ObjectLocation.parse("job.yaml#main.sources/input")
    private val key = DataSourceShapeStore.Key.of(source, DataManifest(emptyList()))


    @Test
    fun invalidationPreventsDeleteRecreateAbaAndUnmountCompletion() {
        val epochs = DataSourceShapeStore.Epochs()
        val beforeDelete = epochs.issue(key)
        epochs.invalidate(key)
        val afterRecreate = epochs.issue(key)

        assertFalse(epochs.isCurrent(key, beforeDelete))
        assertTrue(epochs.isCurrent(key, afterRecreate))

        epochs.invalidateAll()
        assertFalse(epochs.isCurrent(key, afterRecreate))
    }


    @Test
    fun aggregateIsOrderedTabularUnionOnlyAfterEveryPartSucceeds() {
        val a = DataSourceShapeStore.PartState(
            false, DataShape.Tabular(HeaderListing.ofUnique(listOf("a", "shared"))), null)
        val b = DataSourceShapeStore.PartState(
            false, DataShape.Tabular(HeaderListing.ofUnique(listOf("shared", "b"))), null)
        assertEquals(
            listOf("a", "shared", "b"),
            DataSourceShapeStore.aggregate(listOf(a, b))?.header?.values?.map { it.text })

        assertNull(DataSourceShapeStore.aggregate(listOf(a.copy(inspecting = true), b)))
        assertNull(DataSourceShapeStore.aggregate(listOf(a.copy(error = "failed"), b)))
        assertNull(DataSourceShapeStore.aggregate(listOf(
            a, DataSourceShapeStore.PartState(false, DataShape.Payload(TypeMetadata.string), null))))
    }
}
