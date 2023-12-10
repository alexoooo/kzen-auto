package tech.kzen.auto.server.objects.registry

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryReflection
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.platform.ClassName


@Reflect
class ObjectRegistryDocument(
    private val classes: ClassListSpec
):
    DocumentArchetype(),
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val builder = mutableListOf<ExecutionValue>()

        for (className in classes.classNames) {
            val reflection = reflect(className)
            builder.add(reflection.asExecutionValue())
        }

        return ExecutionResult.success(ListExecutionValue(builder))
    }


    private fun reflect(className: ClassName): ObjectRegistryReflection {
        val clazz: Class<*>?
        try {
            clazz = Class.forName(className.asString())
        }
        catch (t: Throwable) {
            return ObjectRegistryReflection(null, ExceptionUtils.message(t))
        }

        val source = clazz.protectionDomain.codeSource.location.toString()
        return ObjectRegistryReflection(source, null)
    }
}