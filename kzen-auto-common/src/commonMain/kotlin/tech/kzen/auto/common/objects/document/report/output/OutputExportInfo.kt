package tech.kzen.auto.common.objects.document.report.output


data class OutputExportInfo(
    val message: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val messageKey = "message"


        fun fromCollection(collection: Map<String, Any?>): OutputExportInfo {
            return OutputExportInfo(
                collection[messageKey] as String)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        return mapOf(
            messageKey to message)
    }
}