package tech.kzen.auto.server

import tech.kzen.auto.common.getAnswerFoo
import org.junit.Assert.assertEquals
import kotlin.test.Test


class ServerTest {
    @Test
    fun `simple test`() {
        assertEquals(42, getAnswerFoo())
    }
}
