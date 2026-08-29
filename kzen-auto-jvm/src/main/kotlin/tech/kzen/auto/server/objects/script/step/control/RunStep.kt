package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.logic.TypeAssignability
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class RunStep(
    private val instructions: ObjectLocation,
    private val arguments: Map<String, ObjectLocation>,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    // Host the linked Logic ([instructions]) as a confined child node, resolving each declared argument from this
    // run's in-scope values at call time. The engine owns the child's stepping, so step-over / step-out cross the
    // boundary uniformly; the child result is this step's value (recorded + traced by the enclosing sequence).
    //
    // The `contexts:` map takes no part HERE and that is deliberate: like every context declaration it is
    // notation the running step carries, so the run context reads it off this step and installs the borrows on
    // the child's frame itself (`ScriptRunContext.callSiteBindings`). Arguments are values this step computes;
    // contexts are a declaration it merely holds.
    override suspend fun run(execution: StepExecution): Any? {
        val schema = BindingSchema.of(arguments.keys.map { name ->
            BindingDefinition(BindingName(name), DataContract(DataType.Dynamic(nullable = true)))
        })
        val argumentValues = DataBindings.bind(schema, arguments.map { (name, argumentLocation) ->
            BindingName(name) to JobDataValues.lift(execution.referencedValue(argumentLocation))
        })

        val result = execution.host(instructions, argumentValues)
        val main = BindingName("main")
        return if (result.schema.find(main) == null) null
        else when (val state = result[main]) {
            BindingState.Unbound -> null
            is BindingState.Bound -> JobDataValues.boundary(state.value)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        // A RunStep yields whatever its linked logic returns. For a sub-Script the output type is declared:
        // its `results` signature (void when none is declared), so surface that rather than a blanket Any.
        // For any other linked logic (e.g. a Flow) the output type isn't declared anywhere we can read, so
        // fall back to Any instead of mislabelling it void.
        val graphNotation = scriptDefinitionContext.graphNotation
        val instructionsDocument = graphNotation.documents[instructions.documentPath]

        val returnSignature =
            if (instructionsDocument != null && ScriptConventions.isScript(instructionsDocument)) {
                ResultSignatureDefiner.parse(
                    graphNotation.firstAttribute(instructions, ScriptConventions.resultsAttributePath))
            }
            else if (instructionsDocument != null && JobConventions.isJob(instructionsDocument)) {
                BindingSchema.of(BindingDefinition(
                    BindingName("main"),
                    DataContract(DataType.Dynamic(nullable = false))))
            }
            else {
                BindingSchema.of(BindingDefinition(
                    BindingName("main"),
                    DataContract(DataType.Dynamic(nullable = true))))
            }

        val mismatch = callBindingMismatch(graphNotation)
        if (mismatch != null) {
            return ScriptStepDefinition(null, mismatch)
        }

        return ScriptStepDefinition.of(returnSignature)
    }


    /**
     * The static half of the `contexts:` conformance the runtime re-checks by raw class: source declaration to
     * target declaration, reported against the call that wired them rather than surfacing as a
     * `ClassCastException` inside the callee.
     *
     * **There is no `Any` escape here, and its absence is the point** — this is where CX7b diverges from
     * [tech.kzen.auto.server.objects.script.step.context.BindStep], which must skip its class comparison when
     * inference yields `Any`. `BindStep` compares against an INFERRED type, where `Any` is the approximation
     * `ExpressionReturnTypeInference` writes for "the graph cannot name this type" and rejecting it would
     * reject every plugin class. Both sides here are DECLARED: a Context whose contract says `Any` is a
     * genuine top type, and admitting it into a `RemoteWebDriver` slot would be unsound, not generous. Same
     * word, opposite meaning, opposite handling.
     *
     * A dangling side is skipped rather than reported: it is not a type question, and
     * [tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis] already warns about it —
     * naming the reference the author actually typed, which this could not.
     */
    private fun callBindingMismatch(graphNotation: GraphNotation): String? {
        val callBindings = LogicContextConventions.stepCallContexts(graphNotation, selfLocation)
        if (callBindings.isEmpty()) {
            return null
        }

        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        for (callBinding in callBindings) {
            val target = callBinding.target ?: continue
            val source = callBinding.source ?: continue

            // The overwhelmingly common case — one Context wired into a slot of the same contract — and the
            // one worth answering without a probe compile.
            if (source.type == target.type) {
                continue
            }

            if (source.type.nullable && ! target.type.nullable) {
                return "${target.label()} holds ${target.typeLabel()}, which is not nullable, " +
                        "but ${source.label()} can hold null"
            }

            if (TypeAssignability.isAssignable(source.type, target.type, cachedKotlinCompiler, classLoader)) {
                continue
            }

            return "${target.label()} holds ${target.typeLabel()}, " +
                    "which ${source.label()}'s ${source.type.toSimple()} cannot be bound to"
        }

        return null
    }
}
