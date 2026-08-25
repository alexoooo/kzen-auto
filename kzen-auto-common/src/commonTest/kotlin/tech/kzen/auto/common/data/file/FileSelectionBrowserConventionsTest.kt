package tech.kzen.auto.common.data.file

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentMap


class FileSelectionBrowserConventionsTest {
    @Test
    fun resolvesMarkedBrowserPaths() {
        val metadata = MapAttributeNotation(mapOf(
            FileSelectionBrowserConventions.metadataKey to ScalarAttributeNotation("browser")
        ).toPersistentMap())

        assertEquals(
            AttributePath.parse("browser"),
            FileSelectionBrowserConventions.browserAttributePath(metadata))
        assertEquals(
            AttributePath.parse("browser.directory"),
            FileSelectionBrowserConventions.directoryAttributePath(metadata))
        assertEquals(
            AttributePath.parse("browser.filter"),
            FileSelectionBrowserConventions.filterAttributePath(metadata))
    }


    @Test
    fun absentMarkerKeepsNavigationTransient() {
        assertNull(FileSelectionBrowserConventions.browserAttributePath(MapAttributeNotation.empty))
        assertNull(FileSelectionBrowserConventions.directoryAttributePath(MapAttributeNotation.empty))
        assertNull(FileSelectionBrowserConventions.filterAttributePath(MapAttributeNotation.empty))
    }
}
