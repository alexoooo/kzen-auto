package tech.kzen.auto.server.objects.script.step.eval

import tech.kzen.auto.common.util.ExpressionUtils
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
    fun generateCode(
        mainClassName: String,
        returnType: String,
        code: String,
        scope: Map<ObjectPath, TypeMetadata>
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

        val generatedCode = """
$imports

class $mainClassName: ${ StepExpression::class.java.simpleName } {
    private var predecessorValues: List<Any?> = listOf()

    ${accessors.joinToString("\n")}

    override fun evaluate(predecessorValues: List<Any?>): $returnType {
        this.predecessorValues = predecessorValues
        return run {
$code
        }
    }
}
"""
        return KotlinCode(
            mainClassName,
            generatedCode)
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
