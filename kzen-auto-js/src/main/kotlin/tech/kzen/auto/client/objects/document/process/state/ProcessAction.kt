package tech.kzen.auto.client.objects.document.process.state


//---------------------------------------------------------------------------------------------------------------------
sealed class ProcessAction


//---------------------------------------------------------------------------------------------------------------------
object InitiateProcessEffect: ProcessAction()


//---------------------------------------------------------------------------------------------------------------------
object ListInputsRequest: ProcessAction()


sealed class ListInputsResponse: ProcessAction()


data class ListInputsResult(
    val fileListing: List<String>
): ListInputsResponse()


data class ListInputsError(
    val message: String
): ListInputsResponse()


//---------------------------------------------------------------------------------------------------------------------
object ListColumnsRequest: ProcessAction()


data class ListColumnsResponse(
    val columnListing: List<String>
): ProcessAction()


data class ListColumnsError(
    val message: String
): ProcessAction()


