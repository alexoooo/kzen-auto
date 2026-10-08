package tech.kzen.auto.common.data.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class ConfiguredFormatDetailTest {
    @Test
    fun perFileOverrideAvailabilityRoundTripsThroughTheCatalogValue() {
        val detail = ConfiguredFormatDetail(
            "formats.yaml#Automatic",
            "Automatic",
            emptyList(),
            columnLockingAvailable = true,
            perFileOverrideAvailable = false)

        val decoded = ConfiguredFormatDetail.ofCollection(detail.asCollection())

        assertFalse(decoded.perFileOverrideAvailable)
        assertTrue(decoded.columnLockingAvailable)
    }


    @Test
    fun projectDocumentRoundTripsAndIsAbsentForABuiltIn() {
        val project = ConfiguredFormatDetail(
            "main/Custom.yaml#main.objects/Measurements", "Measurements", emptyList(), projectDocument = "Custom")
        val builtIn = ConfiguredFormatDetail("formats.yaml#CSV", "CSV", listOf("csv"))

        assertEquals("Custom", ConfiguredFormatDetail.ofCollection(project.asCollection()).projectDocument)
        assertNull(ConfiguredFormatDetail.ofCollection(builtIn.asCollection()).projectDocument)
    }


    @Test
    fun olderCatalogValuesRemainEligibleForPerFileSelection() {
        val encoded = ConfiguredFormatDetail(
            "formats.yaml#CSV",
            "CSV",
            listOf("csv"))
            .asCollection() - setOf("perFileOverrideAvailable", "columnLockingAvailable", "writable")

        val decoded = ConfiguredFormatDetail.ofCollection(encoded)

        assertTrue(decoded.perFileOverrideAvailable)
        assertFalse(decoded.columnLockingAvailable)
        assertFalse(decoded.writable)
    }


    @Test
    fun writabilityRoundTripsThroughTheCatalogValue() {
        val csv = ConfiguredFormatDetail("formats.yaml#CSV", "CSV", listOf("csv"), writable = true)
        val text = ConfiguredFormatDetail("formats.yaml#PlainText", "Plain text", listOf("txt"))

        assertTrue(ConfiguredFormatDetail.ofCollection(csv.asCollection()).writable)
        assertFalse(ConfiguredFormatDetail.ofCollection(text.asCollection()).writable)
    }
}
