package tech.kzen.auto.common.util.data


data class DataLocationGroup(
    val group: String?
): Comparable<DataLocationGroup> {
    companion object {
        val empty = DataLocationGroup(null)
        val other = DataLocationGroup("other")
    }

    override fun compareTo(other: DataLocationGroup): Int {
        return when {
            group == other.group ->
                0

            group == null ->
                -1

            other.group == null ->
                1

            else ->
                group.compareTo(other.group)
        }
    }
}