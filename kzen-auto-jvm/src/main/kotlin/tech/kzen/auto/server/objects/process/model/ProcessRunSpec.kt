package tech.kzen.auto.server.objects.process.model

import tech.kzen.auto.common.objects.document.process.FilterSpec
import tech.kzen.auto.common.objects.document.process.PivotSpec
import java.nio.file.Path


data class ProcessRunSpec(
    val inputs: List<Path>,
    val columnNames: List<String>,
    val filter: FilterSpec,
    val pivot: PivotSpec
) {
    fun toSignature(): ProcessRunSignature {
        return ProcessRunSignature(
            inputs,
            columnNames,
            filter,
            pivot.rows,
            pivot.values.columns.keys
        )
    }
}