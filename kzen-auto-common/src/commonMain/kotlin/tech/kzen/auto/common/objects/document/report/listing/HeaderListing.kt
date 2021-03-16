package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class HeaderListing(
    val values: List<String>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        val duplicates = values.groupBy { it }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicates detected: $duplicates"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun append(addend: HeaderListing): HeaderListing {
        return HeaderListing(values + addend.values)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleUnorderedList(values.map { Digest.ofUtf8(it) })
    }
}