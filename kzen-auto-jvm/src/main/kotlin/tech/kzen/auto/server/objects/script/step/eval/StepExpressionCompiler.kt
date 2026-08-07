package tech.kzen.auto.server.objects.script.step.eval

import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * Generates the Kotlin source for a user expression that runs in the context of a Script step: a class
 * implementing [StepExpression] whose `evaluate` exposes each in-scope value as a typed property (by its
 * step/binding name) and wraps the user's `code` in a `run { … }` block.
 *
 * Shared by [FormulaStep] (which probes several `returnType`s to infer the value type) and `DoWhileStep`
 * (which forces `returnType = "Boolean"`, since a loop condition is required to be Boolean — no inference).
 * The `scope` map's iteration order defines the accessor index, so the caller must pass runtime values to
 * the compiled `evaluate` in the same order.
 */
object StepExpressionCompiler {
    // The inference-mode member holding the user's expression as a lambda: the property's type is left to the
    // compiler to infer (`() -> T`), so [FormulaStep] recovers the expression's value type by reflecting the
    // property type's last argument (see ExpressionReturnTypeInference, which owns the property-name
    // contract). A lambda-valued property rather than an expression-body function because K2 refuses a
    // declaration whose own inferred type is `Nothing` (IMPLICIT_NOTHING_RETURN_TYPE, a hard error regardless
    // of visibility) — `() -> Nothing` is not `Nothing`, so an expression like `error(...)` compiles and
    // fails at run time as the user intends.
    private const val probePropertyName = ExpressionReturnTypeInference.probePropertyName


    // The forced-return form: `evaluate` returns exactly [returnType], so a value that does not conform is a
    // compile error the caller surfaces as the step's validation error ([ResultStep] against its declared result
    // type, [DoWhileStep] against Boolean).
    fun generateCode(
        mainClassName: String,
        returnType: String,
        code: String,
        scope: Map<ObjectPath, TypeMetadata>
    ): KotlinCode {
        return generate(mainClassName, scope, evaluateReturnType = returnType, probe = false, code = code)
    }


    // The inference form: the user's expression is an inferred [probePropertyName] member and `evaluate` delegates
    // to it. [FormulaStep] uses this for both validation (reflect the probe's value type) and execution (call
    // `evaluate`), so a single content signature compiles once and serves both.
    fun generateInferenceCode(
        mainClassName: String,
        code: String,
        scope: Map<ObjectPath, TypeMetadata>
    ): KotlinCode {
        return generate(mainClassName, scope, evaluateReturnType = "Any?", probe = true, code = code)
    }


    private fun generate(
        mainClassName: String,
        scope: Map<ObjectPath, TypeMetadata>,
        evaluateReturnType: String,
        probe: Boolean,
        code: String = ""
    ): KotlinCode {
        val imports = generateImports(scope.values)

        val accessors = scope
            .entries
            .withIndex()
            .map {
                val entry = it.value
                // The accessor name is the canonical escape of the step/binding name (plain when it is a valid
                // identifier, back-ticked otherwise), so the identifier a user writes in the expression is exactly
                // what KotlinExpressionAnalyzer extracts and rewrites. Both forms compile to the same property.
                val accessorName = ExpressionUtils.escapeKotlinVariableName(entry.key.name.value)
                "val $accessorName get(): ${entry.value.toSimple()} {" +
                "    return predecessorValues[${it.index}] as ${entry.value.toSimple()}" +
                "}"
            }

        // The user's code is concatenated between an explicit header and footer rather than interpolated into
        // one template, so the generated source states WHERE the code went: [KotlinCode.UserCodeRegion] is
        // computed from the header's length, which is what lets a compiler diagnostic's position be mapped
        // back to an offset in the user's own text. Searching for the code instead would be wrong for an
        // empty expression and for one that also occurs in an accessor name.
        val bodyHeader: String
        val bodyFooter: String
        if (probe) {
            bodyHeader = """
    val $probePropertyName = { run {
"""
            bodyFooter = """
    } }

    override fun evaluate(predecessorValues: List<Any?>): $evaluateReturnType {
        this.predecessorValues = predecessorValues
        return $probePropertyName()
    }
"""
        }
        else {
            bodyHeader = """
    override fun evaluate(predecessorValues: List<Any?>): $evaluateReturnType {
        this.predecessorValues = predecessorValues
        return run {
"""
            bodyFooter = """
        }
    }
"""
        }

        val header = """
$imports

class $mainClassName: ${ StepExpression::class.java.simpleName } {
    private var predecessorValues: List<Any?> = listOf()

    ${accessors.joinToString("\n")}
""" + bodyHeader

        val footer = bodyFooter + """
}
"""

        return KotlinCode(
            mainClassName,
            header + code + footer,
            KotlinCode.UserCodeRegion(header.length, code.length))
    }


    fun sanitizeName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }


    private fun generateImports(importTypeMetadata: Collection<TypeMetadata>): String {
        val classNames = importTypeMetadata.flatMap { it.classNames() }.toSet()

        val basicClassNames = setOf(
            StepExpression::class.java.name)

        val classImports = basicClassNames + classNames

        return classImports.joinToString("\n") {
            "import $it"
        }
    }
}
