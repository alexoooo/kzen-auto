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
 * A Report document as an engine [Logic] (the fourth kzen-auto paradigm on the engine, beside Script, Flow
 * and Job). Thin and immutable: [ReportLogicCompiler] resolves the [reportRunContext] and report services
 * once, and each [run] makes a fresh [ReportRun] that drives that call's disruptor record pipeline.
 *
 * The signature mirrors [tech.kzen.auto.server.objects.report.ReportDocument.define] — **no declared inputs**,
 * because a Report is configured entirely from its own notation (its input selection, formulas and output
 * spec), not from an argument tuple. A caller therefore passes it nothing; it still returns a `main` component.
 *
 * **A Report IS hostable**, like every other [tech.kzen.auto.common.paradigm.logic.LogicDocument] — a `RunStep`
 * or a Flow's `RunLogic` vertex can target one, and [tech.kzen.auto.server.exec.LogicCompiler] reaches it by the
 * same polymorphic dispatch as any other flavour (pinned by [ReportHostedTest]).
 *
 * ⚠ Two things are still **top-level-shaped** when a Report is hosted, and neither fails loudly:
 * [ReportRun] registers its preview / summary handler on its OWN node via `Execution.onRequest`, while the
 * client addresses the run's ROOT frame — so a hosted Report's online output info silently answers nothing
 * even though the pipeline itself runs to completion; and the run dir is stamped with the compiling run's
 * [LogicRunExecutionId], which for a hosted Report is the HOST's run identity rather than its own, so the
 * offline progress correlation points at the wrong frame. Execution is unaffected; only the UI feedback path is.
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
