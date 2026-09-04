package tech.kzen.auto.client.objects.document.common.file

import kotlin.test.Test
import kotlin.test.assertNull


class FileSelectionTableTest {
    @Test
    fun inlineFailureIsNotRepeatedWhenDetailsAreShown() {
        val presentation = FileResolutionPresentation.of(
            false,
            null,
            "Choose a format or encoding.")

        assertNull(FileSelectionTable.additionalError(presentation))
    }
}
