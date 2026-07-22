package tech.kzen.auto.server.objects.script.api


/**
 * A [ScriptStep] that is a referenceable, typed VALUE rather than an executable step: it contributes a type to
 * validation (via [definition]) and can be referenced by other steps by name, but it is never placed in an
 * executed step sequence, so it has no [run]. The engine supplies its value directly — a
 * [tech.kzen.auto.server.objects.logic.ParameterBinding] from the run arguments, a
 * [tech.kzen.auto.server.objects.script.binding.ForEachItemBinding] from the enclosing loop.
 *
 * This exists because [ScriptStep.run] is abstract (a runnable step MUST declare execution — no hidden fallback):
 * a binding declares, explicitly and once here, that invoking it is a usage error rather than each binding
 * re-stating it. A binding reached as an executable step is a structural bug, hence the hard failure.
 */
abstract class ScriptValueBinding: ScriptStep {
    final override suspend fun run(execution: StepExecution): Any? {
        throw UnsupportedOperationException(
            "${this::class.simpleName} is a referenceable value binding, not an executable step")
    }
}
