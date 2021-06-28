package tech.kzen.auto.server.objects.report.pipeline.output.export.model

import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec


enum class ExportCompression {
    None,
    Zip,
    GZip;

    companion object {
        fun byName(compressionName: String): ExportCompression {
            return when (compressionName) {
                OutputExportSpec.compressionNoneName ->
                    None

                OutputExportSpec.compressionZipName ->
                    Zip

                OutputExportSpec.compressionGzName ->
                    GZip

                else ->
                    TODO("Compression not supported (yet): $compressionName")
            }
        }
    }
}