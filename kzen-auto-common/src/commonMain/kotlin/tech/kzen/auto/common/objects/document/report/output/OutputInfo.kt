package tech.kzen.auto.common.objects.document.report.output


data class OutputInfo(
    val runDir: String,
    val table: OutputTableInfo?,
    val export: OutputExportInfo?,
    val status: OutputStatus
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val runDirKey = "work"
        private const val statusKey = "status"
        private const val tableKey = "table"
        private const val exportKey = "export"


        fun fromCollection(collection: Map<String, Any?>): OutputInfo {
//            println("^^^ OutputInfo ## fromCollection - $collection")

            @Suppress("UNCHECKED_CAST")
            val tableMap = collection[tableKey] as? Map<String, Any?>
            val table = tableMap?.let {
                OutputTableInfo.fromCollection(it)
            }

            @Suppress("UNCHECKED_CAST")
            val exportMap = collection[exportKey] as? Map<String, Any?>
            val export = tableMap?.let {
                OutputExportInfo.fromCollection(it)
            }

            val status = OutputStatus.valueOf(
                collection[statusKey] as String)

            return OutputInfo(
                collection[runDirKey] as String,
                table,
                export,
                status)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        val builder = mutableMapOf<String, Any?>()
        builder[runDirKey] = runDir

        if (table != null) {
            builder[tableKey] = table.toCollection()
        }
        
        if (export != null) {
            builder[exportKey] = export.toCollection()
        }

        builder[statusKey] = status.name

        return builder
    }
}