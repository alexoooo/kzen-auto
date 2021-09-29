package tech.kzen.auto.common.objects.document.report.listing


data class InputSelectedInfo(
    val locations: List<InputDataInfo>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofCollection(collection: List<Map<String, Any>>): InputSelectedInfo {
            val locations = collection.map { InputDataInfo.ofCollection(it) }
            return InputSelectedInfo(locations)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return locations.isEmpty()
    }


    fun sorted(): InputSelectedInfo {
        return InputSelectedInfo(locations.sorted())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asCollection(): List<Map<String, Any>> {
        return locations
            .map { it.asCollection() }
    }
}
