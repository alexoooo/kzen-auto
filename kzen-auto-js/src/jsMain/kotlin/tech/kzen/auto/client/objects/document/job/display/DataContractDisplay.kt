package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.lib.common.exec.data.shape.DataShape
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType


sealed interface DataContractDisplay {
    /** [provenance] says where a type read from data came from (see [StepValidation.provenance]). */
    data class Contract(
        val contract: DataContract,
        val shape: DataShape? = null,
        val provenance: String? = null
    ): DataContractDisplay
    data object Dynamic: DataContractDisplay
    /** Not known yet: the server is still reading data to type it. */
    data object Reading: DataContractDisplay
    data object Unavailable: DataContractDisplay
    data class Error(val message: String): DataContractDisplay
    data object Loading: DataContractDisplay

    companion object {
        fun of(validation: StepValidation?): DataContractDisplay {
            if (validation == null) {
                return Loading
            }
            val errorMessage = validation.errorMessage
            if (errorMessage != null) {
                return Error(errorMessage)
            }
            val contract = validation.contract ?: return Unavailable
            return when {
                contract.structural !is DataType.Dynamic -> Contract(contract, provenance = validation.provenance)
                validation.partial -> Reading
                else -> Dynamic
            }
        }

        fun of(
            result: DataShapeResult?,
            loading: Boolean = false,
            error: String? = null
        ): DataContractDisplay = when {
            error != null -> Error(error)
            loading -> Loading
            result == null || result == DataShapeResult.Unavailable -> Unavailable
            result is DataShapeResult.Observed && result.shape.itemType.structural is DataType.Dynamic -> Dynamic
            result is DataShapeResult.Observed -> Contract(result.shape.itemType, result.shape)
            else -> Unavailable
        }
    }
}
