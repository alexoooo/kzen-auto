package tech.kzen.auto.client.objects.document.job.source

import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
    fun aggregatePreservesCompleteShapeOnlyAfterEveryPartSucceeds() {
        val common = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a", "shared")))
        val a = DataSourceShapeStore.PartState(
            false, DataShapeResult.Observed(common), null)
        val b = DataSourceShapeStore.PartState(
            false, DataShapeResult.Observed(common), null)
        assertEquals(
            DataShapeResult.Observed(common),
            DataSourceShapeStore.aggregate(listOf(a, b)))

        assertNull(DataSourceShapeStore.aggregate(listOf(a.copy(inspecting = true), b)))
        assertNull(DataSourceShapeStore.aggregate(listOf(a.copy(error = "failed"), b)))
        assertEquals(
            DataShapeResult.Unavailable,
            DataSourceShapeStore.aggregate(listOf(
                a, DataSourceShapeStore.PartState(
                    false,
                    DataShapeResult.Observed(LegacyDataShapeBridge.payload(TypeMetadata.string)),
                    null))))
        assertEquals(
            DataShapeResult.Unavailable,
            DataSourceShapeStore.aggregate(listOf(
                a, DataSourceShapeStore.PartState(false, DataShapeResult.Unavailable, null))))
    }


    @Test
    fun partLookupRequiresTheExactSourceAndResolvedPartIdentity() {
        val part = DataPart(
            DataRole.main,
            DataRef(null, "orders.csv"),
            null,
            ResolvedReadSpec(
                ReaderCapabilityIdentity("kzen", "delimited", "1"),
                emptyList(),
                MapExecutionValue(emptyMap())))
        val manifest = DataManifest(listOf(DataUnit(emptyMap(), listOf(part))))
        val observed = DataSourceShapeStore.PartState(
            false,
            DataShapeResult.Observed(
                LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("id")))),
            null)
        val store = DataSourceShapeStore(
            ClientRestApi(""),
            mapOf(
                DataSourceShapeStore.Key.of(source, manifest) to
                    DataSourceShapeStore.State(mapOf(part to observed), observed.result)))

        assertSame(observed, store.partState(source, part))
        assertNull(store.partState(ObjectLocation.parse("job.yaml#other"), part))
        assertNull(store.partState(source, part.copy(ref = DataRef(null, "other.csv"))))
    }
}
