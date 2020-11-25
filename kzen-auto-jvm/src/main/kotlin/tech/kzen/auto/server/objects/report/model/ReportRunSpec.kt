package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.spec.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.PivotSpec
import java.nio.file.Path


data class ReportRunSpec(
    val inputs: List<Path>,
    val columnNames: List<String>,
    val filter: FilterSpec,
    val pivot: PivotSpec
) {
    fun toSignature(): ReportRunSignature {
        return ReportRunSignature(
            inputs,
            columnNames,
            filter.filterNonEmpty(),
            pivot.rows,
            pivot.values.columns.keys
        )
    }
}