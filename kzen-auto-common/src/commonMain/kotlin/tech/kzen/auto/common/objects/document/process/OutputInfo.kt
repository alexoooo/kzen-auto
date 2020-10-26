package tech.kzen.auto.common.objects.document.process


data class OutputInfo(
    val absolutePath: String,
    val modifiedTime: String?//,
//    val folderExists: Boolean
) {
    companion object {
        private const val pathKey = "path"
        private const val modifiedTimeKey = "modified"
//        private const val folderExistsKey = "has-folder"


        fun fromCollection(collection: Map<String, Any?>): OutputInfo {
            return OutputInfo(
                collection[pathKey] as String,
                collection[modifiedTimeKey] as String?/*,
                collection[folderExistsKey] as Boolean*/)
        }
    }


    fun toCollection(): Map<String, Any?> {
        return mapOf(
            pathKey to absolutePath,
            modifiedTimeKey to modifiedTime/*,
            folderExistsKey to folderExists*/)
    }
}