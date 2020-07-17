package tech.kzen.auto.client.objects.document.process.state


//---------------------------------------------------------------------------------------------------------------------
sealed class ProcessAction


//---------------------------------------------------------------------------------------------------------------------
object ListInputsRequest: ProcessAction()


data class ListInputsResponse(
    val fileListing: List<String>
): ProcessAction()


data class ListInputsError(
    val message: String
): ProcessAction()

