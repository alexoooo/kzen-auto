package tech.kzen.auto.server.data

import org.junit.Test
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.server.util.WorkUtils
import kotlin.test.assertEquals


class ColumnListingActionTest {
    private val format = CommonPluginCoordinate.ofString("CSV")
    private val encoding = CommonDataEncodingSpec.ofString("UTF-8")


    @Test
    fun exactFingerprintHitsAndFreshFingerprintExtractsAgain() {
        val action = ColumnListingAction(SchemaCache(WorkUtils.temporary("column-listing")))
        var extracts = 0
        fun extract(): HeaderListing {
            extracts += 1
            return HeaderListing.ofUnique(listOf("column-$extracts"))
        }

        val first = part("10", "20")
        assertEquals(listOf("column-1"), action.headerListing(first, format, encoding, ::extract).values.map { it.text })
        assertEquals(listOf("column-1"), action.headerListing(first, format, encoding, ::extract).values.map { it.text })
        assertEquals(listOf("column-2"), action.headerListing(part("11", "20"), format, encoding, ::extract).values.map { it.text })
        assertEquals(2, extracts)
    }


    @Test
    fun noFingerprintNeverStores() {
        val action = ColumnListingAction(SchemaCache(WorkUtils.temporary("column-listing-plain")))
        val part = DataPart(DataRole.main, DataRef(null, "plain.csv"), null, null)
        var extracts = 0
        repeat(2) {
            action.headerListing(part, format, encoding) {
                extracts += 1
                HeaderListing.ofUnique(listOf("a"))
            }
        }
        assertEquals(2, extracts)
    }


    private fun part(size: String, modified: String) = DataPart(
        DataRole.main,
        DataRef(null, "file.csv", mapOf(DataRef.sizeKey to size, DataRef.modifiedKey to modified)),
        null,
        null)
}
