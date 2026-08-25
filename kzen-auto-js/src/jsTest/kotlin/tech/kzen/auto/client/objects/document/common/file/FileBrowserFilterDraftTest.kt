package tech.kzen.auto.client.objects.document.common.file

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class FileBrowserFilterDraftTest {
    @Test
    fun staleExternalValueDoesNotOverwriteNewerDraft() {
        val submitted = mutableListOf<String>()
        val draft = fixture(submitted)

        draft.edit("first")
        draft.flush()
        draft.edit("newer")

        assertNull(draft.synchronize("first"))
        assertEquals("newer", draft.value)

        draft.flush()
        assertEquals(listOf("first", "newer"), submitted)
    }


    @Test
    fun matchingPublicationSettlesDraftAndRestoresExternalSynchronization() {
        val submitted = mutableListOf<String>()
        val draft = fixture(submitted)

        draft.edit("submitted")
        draft.flush()
        assertNull(draft.synchronize("submitted"))

        assertEquals("external", draft.synchronize("external"))
        assertEquals("external", draft.value)
    }


    @Test
    fun unmountFlushSubmitsPendingDraft() {
        val submitted = mutableListOf<String>()
        val draft = fixture(submitted)

        draft.edit("pending")
        draft.flush()

        assertEquals(listOf("pending"), submitted)
    }


    private fun fixture(submitted: MutableList<String>): FileBrowserFilterDraft =
        FileBrowserFilterDraft(
            initialExternalValue = "initial",
            submit = submitted::add,
            editActivity = { null })
}
