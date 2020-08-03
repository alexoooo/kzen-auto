package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.notation.NotationConventions


data class ProcessState(
    val clientState: SessionState,
    val mainLocation: ObjectLocation,

    val fileListingLoaded: Boolean = false,
    val fileListingLoading: Boolean = false,
    val fileListing: List<String>? = null,
    val fileListingError: String? = null,

    val columnListingLoaded: Boolean = false,
    val columnListingLoading: Boolean = false,
    val columnListing: List<String>? = null,
    var columnListingError: String? = null,

    val tableSummaryTaskRunning: Boolean = false,

    val filterTaskRunning: Boolean = false,

    val filterAddingLoading: Boolean = false,
    val filterAddingError: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val empty = ProcessState(
//            clientState = null,
//            tableSummaryTaskRunning = false,
//            filterTaskRunning = false
//        )

        fun tryCreate(clientState: SessionState): ProcessState? {
//            console.log("^^^ tryCreate: $clientState")
            val mainLocation = tryMainLocation(clientState)
                ?: return null
//            console.log("^^^ tryCreate - got mainLocation: $mainLocation")

            return ProcessState(clientState, mainLocation)
        }


        fun tryMainLocation(clientState: SessionState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (! FilterConventions.isFilter(documentNotation)) {
                return null
            }

            return ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun taskRunning(): Boolean {
        return tableSummaryTaskRunning || filterTaskRunning
    }
}