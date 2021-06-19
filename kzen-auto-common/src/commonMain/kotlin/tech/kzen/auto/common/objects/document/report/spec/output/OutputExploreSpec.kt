package tech.kzen.auto.common.objects.document.report.spec.output

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation


data class OutputExploreSpec(
    val workPath: String,
    val savePath: String,
    val previewStart: Long,
    val previewCount: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val defaultWorkPath = "report"

//        fun ofPreviewRequest(request: DetachedRequest): OutputSpec {
//            val savePath = request.getSingle(ReportConventions.saveFileKey)!!
//            val startRow = request.getLong(ReportConventions.previewStartKey)!!
//            val rowCount = request.getInt(ReportConventions.previewRowCountKey)!!
//            return OutputSpec(savePath, startRow, rowCount)
//        }

        fun ofNotation(notation: MapAttributeNotation): OutputExploreSpec {
            val workPath = notation
                .get(ReportConventions.workDirKey)
                ?.asString()!!

            val savePath = notation
                .get(ReportConventions.saveFileKey)
                ?.asString()!!

            val previewStart = notation
                .get(ReportConventions.previewStartKey)
                ?.asString()
                ?.replace(",", "")
                ?.toLongOrNull()
                ?: throw IllegalArgumentException(
                    "'${ReportConventions.previewStartKey}' attribute notation not found: $notation")

            val previewCount = notation
                .get(ReportConventions.previewRowCountKey)
                ?.asString()
                ?.replace(",", "")
                ?.toIntOrNull()
                ?: throw IllegalArgumentException(
                    "'${ReportConventions.previewRowCountKey}' attribute notation not found: $notation")

            return OutputExploreSpec(workPath, savePath, previewStart, previewCount)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun previewStartZeroBased(): Long {
        return previewStart - 1
    }


    fun isDefaultWorkPath(): Boolean {
        return workPath == defaultWorkPath
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun toPreviewRequest(): DetachedRequest {
//        return DetachedRequest(
//            RequestParams.of(
//                ReportConventions.workDirKey to workPath,
//                ReportConventions.saveFileKey to savePath,
//                ReportConventions.previewStartKey to previewStart.toString(),
//                ReportConventions.previewRowCountKey to previewCount.toString()
//            ), null)
//    }
}