package tech.kzen.auto.common.objects.document.report.listing


data class HeaderLabelMap<T>(
    val map: Map<HeaderLabel, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        HeaderListing.validateUniqueOrdered(map.keys.toList())
    }


    //-----------------------------------------------------------------------------------------------------------------
    val keyHeaderListing: HeaderListing by lazy {
        HeaderListing(map.keys.toList())
    }


    fun append(addend: HeaderLabelMap<T>): HeaderLabelMap<T> {
        val maxIndexLeft: Map<String, Int> = map
            .keys
            .groupBy { it.text }
            .mapValues { e -> e.value.maxOf { it.occurrence } }

        val rightWithOffset: List<HeaderLabel> = addend.map.keys.map {
            val leftIndex: Int = maxIndexLeft[it.text] ?: -1
            val offset = leftIndex + 1
            it.copy(occurrence = it.occurrence + offset)
        }

        val builder = mutableMapOf<HeaderLabel, T>()
        builder.putAll(map)
        for ((index, e) in addend.map.entries.withIndex()) {
            builder[rightWithOffset[index]] = e.value
        }
        return HeaderLabelMap(builder)
    }
}