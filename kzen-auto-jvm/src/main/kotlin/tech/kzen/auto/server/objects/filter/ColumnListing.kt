package tech.kzen.auto.server.objects.filter

import com.google.common.io.MoreFiles
import tech.kzen.auto.common.objects.document.filter.FilterDocument
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.util.AutoJvmUtils
import tech.kzen.lib.common.reflect.Reflect
import java.nio.charset.StandardCharsets
import java.nio.file.Files


@Reflect
object ColumnListing: DetachedAction {
//    companion object {
        private val emptyListing = ExecutionSuccess.ofValue(ExecutionValue.of(emptyList<Any>()))
//    }


    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        val input = request.parameters.get(FilterDocument.inputAttribute.value)
                ?: return ExecutionFailure("'input' required")

        val parsedPath = AutoJvmUtils.parsePath(input)
                ?: return ExecutionFailure("Invalid input: $input")

        val path = parsedPath.toAbsolutePath().normalize()

        if (! Files.isRegularFile(path)) {
            return ExecutionFailure("'input' not a regular file: $path")
        }

        val firstLine= MoreFiles
                .asCharSource(path, StandardCharsets.UTF_8)
                .readFirstLine()
                ?: return emptyListing

        val columnNames = firstLine.split(",")

        return ExecutionSuccess.ofValue(
                ExecutionValue.of(columnNames))
    }
}