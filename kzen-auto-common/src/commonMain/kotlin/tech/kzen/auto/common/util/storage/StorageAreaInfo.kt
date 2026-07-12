package tech.kzen.auto.common.util.storage


data class StorageAreaInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeBytes: Long,
    val bundleCount: Int,
    val deletable: Boolean,
    val budgetBytes: Long?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val idKey = "id"
        private const val displayNameKey = "name"
        private const val descriptionKey = "description"
        private const val sizeKey = "size"
        private const val bundleCountKey = "bundles"
        private const val deletableKey = "deletable"
        private const val budgetKey = "budget"


        fun ofCollection(map: Map<String, String>): StorageAreaInfo {
            return StorageAreaInfo(
                map[idKey]!!,
                map[displayNameKey]!!,
                map[descriptionKey]!!,
                map[sizeKey]!!.toLong(),
                map[bundleCountKey]!!.toInt(),
                map[deletableKey]!!.toBoolean(),
                map[budgetKey]?.toLong()
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, String> {
        val collection = mutableMapOf(
            idKey to id,
            displayNameKey to displayName,
            descriptionKey to description,
            sizeKey to sizeBytes.toString(),
            bundleCountKey to bundleCount.toString(),
            deletableKey to deletable.toString()
        )
        if (budgetBytes != null) {
            collection[budgetKey] = budgetBytes.toString()
        }
        return collection
    }
}
