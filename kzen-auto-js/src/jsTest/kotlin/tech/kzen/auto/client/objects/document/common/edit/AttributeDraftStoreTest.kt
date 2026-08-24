package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class AttributeDraftStoreTest {
    private val objectLocation = ObjectLocation.parse("job.yaml#main.sources/input")
    private val attributePath = AttributePath.ofName(AttributeName("directory"))


    @Test
    fun olderCommittedValueDoesNotClearNewerDraft() {
        val store = AttributeDraftStore()
        store.put(objectLocation, attributePath, "first")
        store.put(objectLocation, attributePath, "second")

        store.remove(objectLocation, attributePath, "first")

        assertEquals("second", store.value(objectLocation, attributePath))
    }


    @Test
    fun matchingCommittedValueClearsDraftWithoutPropRefresh() {
        val store = AttributeDraftStore()
        store.put(objectLocation, attributePath, "committed")

        store.remove(objectLocation, attributePath, "committed")

        assertNull(store.value(objectLocation, attributePath))
    }
}
