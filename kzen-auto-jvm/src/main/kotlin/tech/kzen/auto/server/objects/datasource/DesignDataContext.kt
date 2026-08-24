package tech.kzen.auto.server.objects.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation


class DesignDataContext(
    private val request: ExecutionRequest
): DataContext {
    override fun argument(name: String): Any? {
        return request.getSingle(name)
    }


    override suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue {
        throw UnsupportedOperationException(
            "Resolving this source runs its logic, which requires an active run")
    }


    override suspend fun <R> blocking(block: () -> R): R {
        return withContext(Dispatchers.IO) {
            block()
        }
    }
}
