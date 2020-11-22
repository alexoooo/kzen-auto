package tech.kzen.auto.server.objects.process.model

import tech.kzen.auto.common.objects.document.process.FilterSpec
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import java.nio.file.Path


data class ProcessRunSignature(
    val inputs: List<Path>,
    val columnNames: List<String>,
    val nonEmptyFilter: FilterSpec,
    val pivotRows: Set<String>,
    val pivotValues: Set<String>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    fun hasPivot(): Boolean {
        return pivotRows.isNotEmpty() ||
                pivotValues.isNotEmpty()
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleList(inputs.map { Digest.ofUtf8(it.toString()) })

        builder.addDigestibleList(columnNames.map { Digest.ofUtf8(it) })

        builder.addDigestible(nonEmptyFilter)

        builder.addDigestibleUnorderedList(pivotRows.map { Digest.ofUtf8(it) })
        builder.addDigestibleUnorderedList(pivotValues.map { Digest.ofUtf8(it) })
    }
}