package tech.kzen.auto.client.objects.document.job.source

import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class DataSourceResolveStoreTest {
    private val source = ObjectLocation.parse("job.yaml#main.sources/input")


    @Test
    fun invalidationPreventsDeleteRecreateAba() {
        val epochs = DataSourceResolveStore.Epochs()
        val beforeDelete = epochs.issue(source)

        epochs.invalidate(source)
        val afterRecreate = epochs.issue(source)

        assertFalse(epochs.isCurrent(source, beforeDelete))
        assertTrue(epochs.isCurrent(source, afterRecreate))
    }


    @Test
    fun lifecycleInvalidationRejectsOutstandingEpoch() {
        val epochs = DataSourceResolveStore.Epochs()
        val beforeUnmount = epochs.issue(source)

        epochs.invalidateAll()

        assertFalse(epochs.isCurrent(source, beforeUnmount))
    }
}
