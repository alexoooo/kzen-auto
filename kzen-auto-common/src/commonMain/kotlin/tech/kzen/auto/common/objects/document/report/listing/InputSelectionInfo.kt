package tech.kzen.auto.common.objects.document.report.listing


data class InputSelectionInfo(
//    val dataType: ClassName,
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


    //-----------------------------------------------------------------------------------------------------------------
    fun asCollection(): List<Map<String, Any>> {
        return locations
            .map { it.asCollection() }
    }
}
