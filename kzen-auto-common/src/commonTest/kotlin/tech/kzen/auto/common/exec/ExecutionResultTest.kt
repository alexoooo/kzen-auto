package tech.kzen.auto.common.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import kotlin.test.Test
import kotlin.test.assertEquals


class ExecutionResultTest {
    @Test
    fun nullPointerException() {
        val exception = NullPointerException("foo")
        val executionFailure = ExecutionFailure.ofException(exception)
        assertEquals("Null Pointer: foo", executionFailure.errorMessage)
    }
}