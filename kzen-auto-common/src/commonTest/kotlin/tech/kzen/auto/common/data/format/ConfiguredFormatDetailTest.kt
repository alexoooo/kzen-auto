package tech.kzen.auto.common.data.format

import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun olderCatalogValuesRemainEligibleForPerFileSelection() {
        val encoded = ConfiguredFormatDetail(
            "formats.yaml#CSV",
            "CSV",
            listOf("csv"))
            .asCollection() - setOf("perFileOverrideAvailable", "columnLockingAvailable")

        val decoded = ConfiguredFormatDetail.ofCollection(encoded)

        assertTrue(decoded.perFileOverrideAvailable)
        assertFalse(decoded.columnLockingAvailable)
    }
}
