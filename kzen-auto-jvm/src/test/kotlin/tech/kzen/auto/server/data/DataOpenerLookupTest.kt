package tech.kzen.auto.server.data

import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue


class DataOpenerLookupTest {
    private val plain = object: DataOpener {
        override suspend fun open(context: DataContext, part: DataPart): DataCursor = error("unused")
    }


    @Test
    fun plainReferenceUsesConfiguredPlainOpener() {
        assertSame(plain, DataOpenerLookup(plain).openerFor(DataRef(null, "input.csv")))
    }


    @Test
    fun providerBoundReferenceFailsAtDeferredDispatchBoundary() {
        val failure = assertFailsWith<IllegalStateException> {
            DataOpenerLookup(plain).openerFor(DataRef(DataSourceId("warehouse"), "part-1"))
        }
        assertTrue(failure.message!!.contains("provider-bound refs are not supported yet"))
        assertTrue(failure.message!!.contains("warehouse"))
    }
}
