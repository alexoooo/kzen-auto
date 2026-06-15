package tech.kzen.lib.server.exec.logic.context

import org.junit.Test
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


// Lives in kzen-auto-jvm: kzen-lib-jvm has no test source set; package mirrors the type under test.
class MutableLogicResourceScopeTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun autoDisposesOnSuccessAndOnFailure() {
        assertDisposed(ResourceClosePolicy.Auto, error = false, expectDisposed = true)
        assertDisposed(ResourceClosePolicy.Auto, error = true, expectDisposed = true)
    }


    @Test
    fun manualNeverAutoDisposes() {
        assertDisposed(ResourceClosePolicy.Manual, error = false, expectDisposed = false)
        assertDisposed(ResourceClosePolicy.Manual, error = true, expectDisposed = false)
    }


    @Test
    fun keepOnFailureDisposesOnSuccessButNotFailure() {
        assertDisposed(ResourceClosePolicy.KeepOnFailure, error = false, expectDisposed = true)
        assertDisposed(ResourceClosePolicy.KeepOnFailure, error = true, expectDisposed = false)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun disposesMostRecentlyRegisteredFirst() {
        val scope = MutableLogicResourceScope()
        val order = mutableListOf<String>()
        scope.register("a", ResourceClosePolicy.Auto) { order.add("a") }
        scope.register("b", ResourceClosePolicy.Auto) { order.add("b") }

        scope.disposeAll(error = false)

        assertEquals(listOf("b", "a"), order)
    }


    @Test
    fun deregisteredKeyDoesNotDispose() {
        val scope = MutableLogicResourceScope()
        val fired = mutableListOf<String>()
        scope.register("a", ResourceClosePolicy.Auto) { fired.add("a") }
        scope.deregister("a")

        scope.disposeAll(error = false)

        assertEquals(emptyList<String>(), fired)
    }


    @Test
    fun reRegisterReplacesCloser() {
        val scope = MutableLogicResourceScope()
        val fired = mutableListOf<String>()
        scope.register("a", ResourceClosePolicy.Auto) { fired.add("first") }
        scope.register("a", ResourceClosePolicy.Auto) { fired.add("second") }

        scope.disposeAll(error = false)

        assertEquals(listOf("second"), fired)
    }


    @Test
    fun disposeAllIsIdempotent() {
        val scope = MutableLogicResourceScope()
        var count = 0
        scope.register("a", ResourceClosePolicy.Auto) { count += 1 }

        scope.disposeAll(error = false)
        scope.disposeAll(error = false)

        assertEquals(1, count)
    }


    @Test
    fun failingCloserDoesNotBlockOthers() {
        val scope = MutableLogicResourceScope()
        val fired = mutableListOf<String>()
        scope.register("a", ResourceClosePolicy.Auto) { fired.add("a") }
        scope.register("b", ResourceClosePolicy.Auto) { throw RuntimeException("boom") }
        scope.register("c", ResourceClosePolicy.Auto) { fired.add("c") }

        scope.disposeAll(error = false)

        // Disposal is LIFO (c, b, a); b throwing must not stop the earlier-registered a from running.
        assertEquals(listOf("c", "a"), fired)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parseAcceptsKnownPoliciesAndRejectsOthers() {
        assertEquals(ResourceClosePolicy.Auto, ResourceClosePolicy.parse("auto"))
        assertEquals(ResourceClosePolicy.Manual, ResourceClosePolicy.parse("manual"))
        assertEquals(ResourceClosePolicy.KeepOnFailure, ResourceClosePolicy.parse("keepOnFailure"))
        assertFailsWith<IllegalArgumentException> { ResourceClosePolicy.parse("nope") }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertDisposed(policy: ResourceClosePolicy, error: Boolean, expectDisposed: Boolean) {
        val scope = MutableLogicResourceScope()
        var disposed = false
        scope.register("resource", policy) { disposed = true }

        scope.disposeAll(error)

        assertEquals(expectDisposed, disposed)
    }
}
