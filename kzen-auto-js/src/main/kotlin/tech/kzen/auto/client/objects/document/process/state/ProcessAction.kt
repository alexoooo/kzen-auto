package tech.kzen.auto.client.objects.document.process.state


//---------------------------------------------------------------------------------------------------------------------
sealed class ProcessAction


//---------------------------------------------------------------------------------------------------------------------
object InitiateProcessEffect: ProcessAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class ListInputsAction: ProcessAction()


object ListInputsRequest: ListInputsAction()


sealed class ListInputsResponse: ListInputsAction()


data class ListInputsResult(
    val fileListing: List<String>
): ListInputsResponse()


data class ListInputsError(
    val message: String
): ListInputsResponse()


//---------------------------------------------------------------------------------------------------------------------
sealed class ListColumnsAction: ProcessAction()


object ListColumnsRequest: ListColumnsAction()


data class ListColumnsResponse(
    val columnListing: List<String>
): ListColumnsAction()


data class ListColumnsError(
    val message: String
): ListColumnsAction()


//---------------------------------------------------------------------------------------------------------------------
sealed class FilterAction: ProcessAction()


//--------------------------------------------------------------
data class FilterAddRequest(
    val columnName: String
): FilterAction()


object FilterAddResponse: FilterAction()


data class FilterAddError(
    val message: String
): FilterAction()


//--------------------------------------------------------------
data class FilterRemoveRequest(
    val columnName: String
): FilterAction()


object FilterRemoveResponse: FilterAction()


data class FilterRemoveError(
    val message: String
): FilterAction()
