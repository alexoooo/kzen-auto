package tech.kzen.auto.client.objects.document.report.formula.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.store.MirroredGraphError


class ReportFormulaStore(
    private val store: ReportStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun validateAsync() {
        if (store.state().formulaSpec().formulas.isEmpty()) {
            store.update { state ->
                state.withFormula { it.copy(
                    formulaError = null,
                    formulaMessages = mapOf()
                ) }
            }
            return
        }

        store.update { state ->
            state.withFormula { it.copy(
                formulaError = null,
                formulaLoading = true
            ) }
        }

        async {
            delay(1)
            val validate = validateFormulas()

            delay(10)
            store.update { state ->
                state.withFormula { it.copy(
                    formulaError = validate.errorOrNull(),
                    formulaLoading = false,
                    formulaMessages = validate.valueOrNull() ?: mapOf()
                ) }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun addFormulaAsync(columnName: String) {
        beforeRequest()

        async {
            delay(1)
            val error = submitFormulaAdd(columnName)
            validateAfterNotationChange(error)

            store.input.listColumnsIfFlat()
            store.output.lookupOutputOfflineIfTable()
        }
    }


    fun removeFormulaAsync(columnName: String) {
        beforeRequest()

        async {
            delay(1)
            val error = submitFormulaRemove(columnName)
            validateAfterNotationChange(error)

            store.input.listColumnsIfFlat()
            store.output.lookupOutputOfflineIfTable()
        }
    }


    fun updateFormulaAsync(columnName: String, formula: String) {
        beforeRequest()

        async {
            delay(1)
            val error = submitFormulaUpdate(columnName, formula)
            validateAfterNotationChange(error)

            store.output.lookupOutputOfflineIfTable()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun beforeRequest() {
        store.update { state ->
            state.withFormula { it.copy(
                formulaError = null,
                formulaLoading = true
            ) }
        }
    }


    private suspend fun validateAfterNotationChange(error: String?) {
        if (error != null) {
            delay(10)
            store.update { state ->
                state.withFormula { it.copy(
                    formulaError = error,
                    formulaLoading = false
                ) }
            }
            return
        }

        val validate = validateFormulas()

        delay(10)
        store.update { state ->
            state.withFormula { it.copy(
                formulaError = validate.errorOrNull(),
                formulaLoading = false,
                formulaMessages = validate.valueOrNull() ?: mapOf()
            ) }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun validateFormulas(): ClientResult<Map<String, String>> {
        if (store.state().formulaSpec().formulas.isEmpty()) {
            return ClientResult.ofSuccess(mapOf())
        }

        val result = ClientContext.restClient.performDetached(
            store.mainLocation(),
            ReportConventions.actionParameter to ReportConventions.actionValidateFormulas)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, String>

                ClientResult.ofSuccess(resultValue)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    private suspend fun submitFormulaAdd(
        columnName: String
    ): String? {
        return editNotation(
            FormulaSpec.addCommand(
                store.mainLocation(), columnName))
    }


    private suspend fun submitFormulaRemove(
        columnName: String
    ): String? {
        return editNotation(
            FormulaSpec.removeCommand(
                store.mainLocation(), columnName))
    }


    private suspend fun submitFormulaUpdate(
        columnName: String,
        formula: String
    ): String? {
        return editNotation(
            FormulaSpec.updateFormulaCommand(
                store.mainLocation(), columnName, formula))
    }



    private suspend fun editNotation(
        command: NotationCommand
    ): String? {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)
            ?.error
            ?.let { it.message ?: "$result" }
    }
}