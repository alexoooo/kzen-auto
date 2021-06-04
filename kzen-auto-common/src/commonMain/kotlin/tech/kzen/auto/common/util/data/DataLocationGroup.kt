package tech.kzen.auto.common.util.data


data class DataLocationGroup(
    val group: String?
) {
    companion object {
        val empty = DataLocationGroup(null)
        val other = DataLocationGroup("other")
    }
}