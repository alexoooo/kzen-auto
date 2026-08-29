package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.model.location.ObjectLocation


class DesignDataContext(
    private val request: ExecutionRequest
): DataContext {
    override fun argument(name: String): Any? {
        if (name == DataSourceConventions.actionParameter || name == DataSourceConventions.sourceParameter) {
            return null
        }
        val values = request.parameters.getAll(name)
        return when (values.size) {
            0 -> null
            1 -> values.single()
            else -> values
        }
    }


    override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
        throw UnsupportedOperationException(
            "Resolving this source runs its logic, which requires an active run")
    }


    override suspend fun <R> blocking(block: () -> R): R {
        return withContext(Dispatchers.IO) {
            block()
        }
    }
}
