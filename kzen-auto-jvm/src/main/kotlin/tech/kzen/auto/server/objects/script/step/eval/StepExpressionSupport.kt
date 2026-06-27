package tech.kzen.auto.server.objects.script.step.eval

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassNames


/**
 * Shared expression-evaluation plumbing for the Script steps that run a user Kotlin expression
 * ([FormulaStep], [ResultStep]): gathering the in-scope predecessor / binding types, generating the
 * [StepExpression] source via [StepExpressionCompiler], and compiling / loading / evaluating it against
 * the runtime predecessor values. The only thing the steps differ on is the `returnType` (FormulaStep
 * probes several to infer; ResultStep forces the declared result type), so that stays with each step.
 */
object StepExpressionSupport {
    // The in-scope values this step can reference by name: body predecessors plus the parameters / loop
    // items in scope (the bindings, addressable without occupying a body row). A null value means the
    // referenced object's type is not yet resolved (the validator iterates to a fixpoint).
    fun inScopeTypes(
        selfLocation: ObjectLocation,
        scriptTree: ScriptTree,
        scriptValidation: ScriptValidation
    ): Map<ObjectPath, TypeMetadata?> {
        val builder = mutableMapOf<ObjectPath, TypeMetadata?>()

        val predecessors = scriptTree.predecessors(selfLocation.objectPath)
        val bindings = scriptTree.inScopeBindingPaths(selfLocation.objectPath)

        for (predecessor in predecessors + bindings) {
            builder[predecessor] = scriptValidation.stepValidations[predecessor]?.typeMetadata
        }

        return builder
    }


    // The non-Unit subset of fully-resolved in-scope types, or null if any in-scope type is still
    // unresolved (the caller should defer — return null from definition() or fail with the missing keys).
    // Unit-typed predecessors are dropped: they contribute no accessor (a Unit value is not referenceable).
    fun resolveNonUnit(types: Map<ObjectPath, TypeMetadata?>): Map<ObjectPath, TypeMetadata>? {
        val resolved = types
            .filter { it.value != null }
            .mapValues { it.value!! }

        if (resolved.size != types.size) {
            return null
        }

        return resolved.filter { it.value.className != ClassNames.kotlinUnit }
    }


    fun generateCode(
        selfLocation: ObjectLocation,
        returnType: String,
        code: String,
        scope: Map<ObjectPath, TypeMetadata>
    ): KotlinCode {
        val mainClassName = "Eval_" + StepExpressionCompiler.sanitizeName(selfLocation.objectPath.name.value)
        return StepExpressionCompiler.generateCode(
            mainClassName, returnType, code, scope)
    }


    // Compile, load, instantiate and evaluate the expression against the current values of the in-scope
    // predecessors / bindings (resolved on demand via referencedValue, in the same order as [nonUnitTypes]).
    fun evaluate(
        selfLocation: ObjectLocation,
        returnType: String,
        code: String,
        nonUnitTypes: Map<ObjectPath, TypeMetadata>,
        scriptExecutionContext: ScriptExecutionContext,
        compiler: CachedKotlinCompiler
    ): Any? {
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val generatedCode = generateCode(selfLocation, returnType, code, nonUnitTypes)

        val error = compiler.tryCompile(generatedCode, classLoader)
        check(error == null) {
            "Unable to compile: $error - $generatedCode"
        }

        val clazz = compiler.tryLoad(generatedCode, classLoader)
        check(clazz != null) {
            "Unable to load: $generatedCode"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<StepExpression>

        val instance = classCast.getDeclaredConstructor().newInstance()

        val predecessorValues = nonUnitTypes.map {
            val objectLocation = selfLocation.documentPath.toObjectLocation(it.key)
            scriptExecutionContext.referencedValue(objectLocation)
        }

        return instance.evaluate(predecessorValues)
    }
}
