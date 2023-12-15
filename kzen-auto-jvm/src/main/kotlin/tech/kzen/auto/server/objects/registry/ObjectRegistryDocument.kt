package tech.kzen.auto.server.objects.registry

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.objects.document.registry.ObjectRegistryConventions
import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryReflection
import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryScan
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.model.structure.notation.GraphNotation
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
    companion object {
        fun scan(graphNotation: GraphNotation): ObjectRegistryScan {
            val classNames = graphNotation
                .documents
                .map
                .values
                .asSequence()
                .filter { ObjectRegistryConventions.isObjectRegistry(it) }
                .mapNotNull { ObjectRegistryConventions.classesSpec(it) }
                .flatMap { it.classNames }
                .filter { reflect(it).error == null }
                .toSet()

            return ObjectRegistryScan(classNames)
        }


        private fun reflect(className: ClassName): ObjectRegistryReflection {
            val clazz: Class<*>?
            try {
                clazz = Class.forName(className.asString())
            }
            catch (t: Throwable) {
                return ObjectRegistryReflection(null, ExceptionUtils.message(t))
            }

            val source = clazz.protectionDomain.codeSource?.location?.toString()
            return ObjectRegistryReflection(source, null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val reflection = classes.classNames.map { reflect(it) }
        val notation = ObjectRegistryReflection.listAsExecutionValue(reflection)
        return ExecutionResult.success(notation)
    }
}