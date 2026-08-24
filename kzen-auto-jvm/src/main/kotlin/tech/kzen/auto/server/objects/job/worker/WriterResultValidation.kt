package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.server.objects.logic.TypeAssignability
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName


/** DataRef-specific result validation used only by the built-in file writers. */
internal object WriterResultValidation {
    private val yieldedType = TypeMetadata(
        ClassName(DataRef::class.qualifiedName!!), emptyList(), false)


    suspend fun requireRuntime(
        result: String,
        control: JobControl,
        cachedKotlinCompiler: CachedKotlinCompiler,
        classLoader: ClassLoader
    ) {
        if (result.isBlank()) {
            return
        }
        val declaredType = control.results().find(TupleComponentName(result))?.metadata
            ?: error(noResultDeclared(result))
        val compatible = control.runBlockingIo {
            TypeAssignability.isAssignable(
                yieldedType, declaredType, cachedKotlinCompiler, classLoader)
        }
        require(compatible) { mismatch(result, declaredType) }
    }


    fun staticError(
        result: String,
        selfLocation: ObjectLocation,
        context: WorkerLaneContext,
        cachedKotlinCompiler: CachedKotlinCompiler
    ): String? {
        if (result.isBlank()) {
            return null
        }
        val mainLocation = ObjectLocation(
            selfLocation.documentPath, NotationConventions.mainObjectPath)
        val declaredType = ResultSignatureDefiner.parse(
            context.graphStructure.graphNotation.firstAttribute(
                mainLocation, LogicConventions.resultsAttributePath))
            .find(TupleComponentName(result))
            ?.metadata
            ?: return noResultDeclared(result)
        return if (TypeAssignability.isAssignable(
                yieldedType, declaredType, cachedKotlinCompiler, context.classLoader)) {
            null
        }
        else {
            mismatch(result, declaredType)
        }
    }


    private fun noResultDeclared(result: String): String =
        "No result type declared in the Job signature for '$result'"


    private fun mismatch(result: String, declaredType: TypeMetadata): String =
        "Writer result '$result' declares ${declaredType.toSimple()} but the writer yields DataRef"
}
