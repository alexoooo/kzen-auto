package tech.kzen.auto.server.exec.script

import tech.kzen.auto.server.exec.script.step.SequenceStep
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.tuple.TupleValue


/**
 * A Script as a [Logic]: run the step spine to completion and return the captured result, or void when no
 * [ResultStep][tech.kzen.auto.server.exec.script.step.ResultStep] ran (there is no last-step fallback —
 * matching the established Script result contract).
 */
class ScriptLogic(
    private val root: SequenceStep,
    private val parameters: List<ScriptParameter> = listOf(),
    private val logicSignature: LogicSignature = LogicSignature.empty
): Logic {
    override fun signature(): LogicSignature {
        return logicSignature
    }


    override suspend fun run(execution: Execution): TupleValue {
        val context = ScriptRunContext(execution)

        for (parameter in parameters) {
            context.record(
                parameter.stableId,
                execution.inputs.find(parameter.name) ?: parameter.default)
        }

        root.run(context)

        return context.result()
            ?: TupleValue.empty
    }
}
