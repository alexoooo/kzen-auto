package tech.kzen.auto.server.data

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.server.util.WorkUtils
import kotlin.test.assertEquals


class ColumnListingActionTest {
    private val format = CommonPluginCoordinate.ofString("CSV")
    private val encoding = CommonDataEncodingSpec.ofString("UTF-8")

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()


    @Test
    fun exactFingerprintHitsAndFreshFingerprintExtractsAgain() {
        val action = ColumnListingAction(SchemaCache(WorkUtils(temp.newFolder("column-listing").toPath())))
        var extracts = 0
        fun extract(): HeaderListing {
            extracts += 1
            return HeaderListing.ofUnique(listOf("c${extracts - 1}"))
        }

        val first = part("10", "20")
        assertEquals(listOf("c0"), action.headerListing(first, format, encoding, ::extract).values.map { it.text })
        assertEquals(listOf("c0"), action.headerListing(first, format, encoding, ::extract).values.map { it.text })
        assertEquals(listOf("c1"), action.headerListing(part("11", "20"), format, encoding, ::extract).values.map { it.text })
        assertEquals(2, extracts)
    }


    @Test
    fun noFingerprintNeverStores() {
        val action = ColumnListingAction(SchemaCache(WorkUtils(temp.newFolder("column-listing-plain").toPath())))
        val ref = DataRef(null, "plain.csv")
        var extracts = 0
        repeat(2) {
            action.headerListing(ref, format, encoding) {
                extracts += 1
                HeaderListing.ofUnique(listOf("a"))
            }
        }
        assertEquals(2, extracts)
    }


    private fun part(size: String, modified: String) = DataRef(
        null,
        "file.csv",
        mapOf(DataRef.sizeKey to size, DataRef.modifiedKey to modified))
}
