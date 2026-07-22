package tech.kzen.auto.server.objects.report.exec.calc

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord


interface CalculatedColumn<T> {
    // TODO: primitive and Any return type handling for performance
    fun evaluate(
        model: T,
        flatFileRecord: FlatFileRecord,
        headerListing: HeaderListing
    ): ColumnValue


    /**
     * The expression's value as-is, with no [ColumnValue] coercion — the payload lane
     * (a FormulaSourceWorker streaming arbitrary typed objects must not stringify them).
     */
    fun evaluateRaw(
        model: T,
        flatFileRecord: FlatFileRecord,
        headerListing: HeaderListing
    ): Any?


    /**
     * Injects the run-constant declared-parameter values, in the order of the parameter scope the expression
     * was compiled against (see CalculatedColumnEval). Called once after instantiation, BEFORE any evaluate —
     * values are deliberately not baked into the generated source, so the compile cache keys on the parameter
     * TYPES only. Default no-op: an expression compiled without a parameter scope.
     */
    fun setParameters(values: List<Any?>) {}
}
