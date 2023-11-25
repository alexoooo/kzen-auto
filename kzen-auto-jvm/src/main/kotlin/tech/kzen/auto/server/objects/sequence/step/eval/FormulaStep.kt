package tech.kzen.auto.server.objects.sequence.step.eval

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class FormulaStep(
    private val code: String,
    private val selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(FormulaStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(
        stepContext: StepContext
    ): LogicResult {
        logger.info("{} - value = {}", selfLocation, code)

        val compiler = KzenAutoContext.global().cachedKotlinCompiler
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        val code = generateCode()

        val error = compiler.tryCompile(code, classLoader)
        check(error == null) {
            "Unable to compile: $error - $code"
        }

        val clazz = compiler.tryLoad(code, classLoader)
        check(clazz != null) {
            "Unable to load: $code"
        }

        @Suppress("UNCHECKED_CAST")
        val classCast = clazz as Class<StepExpression>

        val instance = classCast.getDeclaredConstructor().newInstance()
        val value = instance.evaluate()
        println("Value: $value")

        traceValue(stepContext, value.toString())

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }


    private fun generateCode(): KotlinCode {
        val sanitizedName = sanitizeName(selfLocation.objectPath.name.value)
        val mainClassName = "Eval_$sanitizedName"

        val imports = generateImports()

        val code = """
$imports

class $mainClassName: ${ StepExpression::class.java.simpleName } {
    override fun evaluate(): Any? {
        return run {
$code
        }
    }
}
"""
        return KotlinCode(
            mainClassName,
            code)
    }


    private fun generateImports(): String {
        val classImports = setOf(
            StepExpression::class.java.name
        )

        return classImports.joinToString("\n") {
            "import $it"
        }
    }


    private fun sanitizeName(text: String): String {
        return text.replace(Regex("\\W+"), "_")
    }
}