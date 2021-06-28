package tech.kzen.auto.server.objects.report.pipeline.output.export.model

import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec


enum class ExportFormat {
    Csv,
    Tsv;

    companion object {
        fun byName(formatName: String): ExportFormat {
            return when (formatName) {
                OutputExportSpec.formatCsvName ->
                    Csv

                OutputExportSpec.formatTsvName ->
                    Tsv

                else ->
                    TODO("Format not supported (yet): $formatName")
            }
        }
    }
}