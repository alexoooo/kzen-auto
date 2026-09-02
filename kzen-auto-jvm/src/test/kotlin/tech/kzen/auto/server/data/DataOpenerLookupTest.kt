package tech.kzen.auto.server.data

import org.junit.Test
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import kotlin.test.assertSame


class DataOpenerLookupTest {
    private val plain = object: DataOpener {
        override suspend fun open(context: DataContext, part: DataPart): DataCursor = error("unused")
    }


    @Test
    fun plainReferenceUsesConfiguredPlainOpener() {
        assertSame(plain, DataOpenerLookup(plain).openerFor(DataRef(null, "input.csv")))
    }


    @Test
    fun providerBoundReferenceReachesTheSameGenericOpenerEndToEnd() = runBlocking {
        var opened: DataPart? = null
        val opener = object: DataOpener {
            override suspend fun open(context: DataContext, part: DataPart): DataCursor {
                opened = part
                return object: DataCursor {
                    override val shape: DataShape = LegacyDataShapeBridge.payload(TypeMetadata.string)
                    override fun hasNext() = false
                    override fun next(): DataValue = error("empty")
                    override fun close() = Unit
                }
            }
        }
        val ref = DataRef(DataSourceId("warehouse"), "part-1")
        val part = configuredTestDataPart(DataRole.main, ref, null)

        DataOpenerLookup(opener).openerFor(ref).open(object: DataContext {
            override fun argument(name: String): Any? = null
            override suspend fun <R> blocking(block: () -> R): R = block()
        }, part).close()

        assertSame(part, opened)
    }
}
