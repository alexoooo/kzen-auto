package tech.kzen.auto.server.exec.report

import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.service.plugin.ReportDefinitionRepository
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue


/**
 * A Report document as a [Logic] on the new engine (the fourth kzen-auto paradigm to run on it, beside Script,
 * Flow and Job) — the coroutine-shaped successor to [tech.kzen.auto.server.objects.report.ReportDocument]'s old
 * `Logic.execute` factory. Thin and immutable: [ReportLogicCompiler] resolves the [reportRunContext] and report
 * services once, and each [run] makes a fresh [ReportRun] that drives that call's disruptor record pipeline. A
 * Report is always top-level (never hosted), so the signature mirrors
 * [tech.kzen.auto.server.objects.report.ReportDocument.define] (no declared inputs).
 */
class ReportLogic(
    private val reportRunContext: ReportRunContext,
    private val reportWorkPool: ReportWorkPool,
    private val runExecutionId: LogicRunExecutionId,
    private val definitionRepository: ReportDefinitionRepository,
    private val calculatedColumnEval: CalculatedColumnEval,
    private val logicSignature: LogicSignature
): Logic {
    override fun signature(): LogicSignature {
        return logicSignature
    }


    override suspend fun run(execution: Execution): TupleValue {
        return ReportRun(
            execution,
            reportRunContext,
            reportWorkPool,
            runExecutionId,
            definitionRepository,
            calculatedColumnEval
        ).run()
    }
}
