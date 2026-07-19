package tech.kzen.auto.server.service.exec

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * The detached executor end-to-end. Driving it at all depends on the closure scoping: the whole
 * server-allowed project graph is not satisfiable in the test environment, while ScriptValidator's
 * own transitive closure is. Repeat calls go through the reused instance and return the same result.
 *
 * ScriptValidator (rather than a purpose-built fixture) because [AutoConventions.serverAllowed]
 * excludes the `test/` nesting - an action declared in test notation never reaches the executor.
 */
class ModelDetachedExecutorTest {
    private val runParent = DocumentPath.parse("test/script-engine-run-test.yaml")


    @Test
    fun repeatedDetachedCallsSucceedOnTheScopedInstance() {
        val context = KzenAutoContext.forTest()
        try {
            val request = ExecutionRequest(
                RequestParams.of(CommonRestApi.paramHostDocumentPath to runParent.asString()),
                null)

            val first = runBlocking {
                context.detachedExecutor.execute(ScriptConventions.scriptValidatorLocation, request)
            }
            val second = runBlocking {
                context.detachedExecutor.execute(ScriptConventions.scriptValidatorLocation, request)
            }

            assertIs<ExecutionSuccess>(first)
            assertEquals(first, second)
        }
        finally {
            context.close()
        }
    }


    @Test
    fun unknownActionLocationFailsWithoutThrowing() {
        val context = KzenAutoContext.forTest()
        try {
            val missing = ObjectLocation(
                DocumentPath.parse("auto-jvm/script/script-jvm.yaml"),
                ObjectPath.parse("NoSuchAction"))

            val result = runBlocking {
                context.detachedExecutor.execute(missing, ExecutionRequest(RequestParams.empty, null))
            }

            // the not-found guard must precede the digest / closure calls, which require a present seed
            assertTrue(assertIs<ExecutionFailure>(result).errorMessage.contains("Not found"))
        }
        finally {
            context.close()
        }
    }
}
