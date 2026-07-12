package tech.kzen.auto.common.util.storage


data class StorageBundleInfo(
    val key: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val active: Boolean
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val keyKey = "key"
        private const val displayNameKey = "name"
        private const val sizeKey = "size"
        private const val lastModifiedKey = "modified"
        private const val activeKey = "active"


        fun ofCollection(map: Map<String, String>): StorageBundleInfo {
            return StorageBundleInfo(
                map[keyKey]!!,
                map[displayNameKey]!!,
                map[sizeKey]!!.toLong(),
                map[lastModifiedKey]!!.toLong(),
                map[activeKey]!!.toBoolean()
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, String> {
        return mapOf(
            keyKey to key,
            displayNameKey to displayName,
            sizeKey to sizeBytes.toString(),
            lastModifiedKey to lastModifiedMillis.toString(),
            activeKey to active.toString()
        )
    }
}
