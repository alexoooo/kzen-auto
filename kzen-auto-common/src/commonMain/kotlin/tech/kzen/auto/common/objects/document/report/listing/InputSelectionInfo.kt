package tech.kzen.auto.common.objects.document.report.listing


data class InputSelectionInfo(
    val locations: List<InputDataInfo>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofCollection(collection: List<Map<String, Any>>): InputSelectionInfo {

            val locations = collection.map { InputDataInfo.ofCollection(it) }
            return InputSelectionInfo(locations)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return locations.isEmpty()
    }


    fun sorted(): InputSelectionInfo {
        return InputSelectionInfo(locations.sorted())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asCollection(): List<Map<String, Any>> {
        return locations
            .map { it.asCollection() }
    }
}
