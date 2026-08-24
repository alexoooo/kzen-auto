package tech.kzen.auto.server.exec.report

import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.report.ReportDocument
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.data.ReportDefinitionRepository
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.platform.ClassName


/**
 * Translates a Report document's notation graph into a [ReportLogic] runnable on the new engine — the Report
 * analogue of [tech.kzen.auto.server.exec.script.ScriptLogicCompiler] / [tech.kzen.auto.server.exec.flow.FlowLogicCompiler].
 *
 * It instantiates the graph (exactly as the old [ReportDocument]'s `execute` path did) to reuse the document's
 * spec → [tech.kzen.auto.server.objects.report.model.ReportRunContext] derivation
 * ([ReportDocument.reportRunContext], which reads input headers / builds the dataset + analysis column info),
 * and resolves the report services from the environment. A null run context (no valid input selected) or a
 * non-Report document throws, which the controller turns into a clean failure-to-start.
 *
 * The run identity ([LogicCompilerServices.runExecutionId]) is threaded in so the run dir is stamped
 * consistently with the controller's trace buffer (offline progress correlation — see [ReportRun]).
 */
object ReportLogicCompiler {
    fun compile(
        reportLocation: ObjectLocation,
        @Suppress("UNUSED_PARAMETER") graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): ReportLogic {
        val documentPath = reportLocation.documentPath

        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), services.graphEnvironment)

        val reportDocument = graphInstance[reportLocation]?.reference as? ReportDocument
            ?: throw IllegalArgumentException("Not a Report document: $reportLocation")

        val reportRunContext = reportDocument.reportRunContext()
            ?: throw IllegalStateException("Report run context unavailable (no valid input?): $reportLocation")

        val reportWorkPool = resolve<ReportWorkPool>(services)
        val definitionRepository = resolve<ReportDefinitionRepository>(services)
        val calculatedColumnEval = resolve<CalculatedColumnEval>(services)

        // Mirrors ReportDocument.define(): no declared inputs, a nominal string output.
        val logicSignature = LogicSignature(
            TupleDefinition.empty,
            TupleDefinition.ofMain(LogicType.string))

        return ReportLogic(
            reportRunContext,
            reportWorkPool,
            services.runExecutionId,
            definitionRepository,
            calculatedColumnEval,
            logicSignature)
    }


    private inline fun <reified T> resolve(services: LogicCompilerServices): T {
        val className = ClassName(T::class.qualifiedName!!)
        return services.graphEnvironment.resolve(className) as? T
            ?: error("Report service not available in environment: $className")
    }
}
