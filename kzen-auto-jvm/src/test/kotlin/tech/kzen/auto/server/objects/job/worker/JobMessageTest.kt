package tech.kzen.auto.server.objects.job.worker

import org.junit.Test
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame


/**
 * Unit test for [JobMessage] — the uniform channel element. Pins the auto-flatten fallback ([JobMessage.flatView]:
 * scalar → shared `value` column via ColumnValue.toText, Map → keyed columns, at most once per message) and the
 * Logic-boundary rule ([JobMessage.boundaryValue]: payload wins; flat-only → ordered column→text Map; empty → null).
 */
class JobMessageTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun scalarPayloadFlattensToSharedValueColumn() {
        val message = JobMessage.ofPayload(13.0)
        assertEquals("13", message.flatView().record.getString(0))
        // The shared constant header (no per-message header allocation on the scalar lane).
        assertSame(JobMessage.valueHeader, message.flatView().header)
    }


    @Test
    fun mapPayloadFlattensToKeyedColumns() {
        val message = JobMessage.ofPayload(linkedMapOf("city" to "Lviv", "amount" to 30))
        assertEquals(HeaderListing.of(listOf("city", "amount")), message.flatView().header)
        assertEquals(listOf("Lviv", "30"), message.flatView().record.toList())
    }


    @Test
    fun flatViewMaterializesAtMostOnce() {
        val message = JobMessage.ofPayload("x")
        val first = message.flatView()
        assertSame(first, message.flatView())
    }


    @Test
    fun flatPartMessageKeepsItsOwnSchema() {
        val header = HeaderListing.of(listOf("a", "b"))
        val record = FlatFileRecord.of(listOf("1", "2"))
        val message = JobMessage.ofFlat(header, record)
        assertSame(header, message.flatView().header)
        assertSame(record, message.flatView().record)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun boundaryValuePayloadWinsOverFlatPart() {
        val message = JobMessage.ofPayload(13.0)
        message.flatView()
        assertEquals(13.0, message.boundaryValue())
    }


    @Test
    fun boundaryValueFlatOnlyMaterializesOrderedMap() {
        val message = JobMessage.ofFlat(
            HeaderListing.of(listOf("city", "amount")),
            FlatFileRecord.of(listOf("Lviv", "30")))
        assertEquals(linkedMapOf("city" to "Lviv", "amount" to "30"), message.boundaryValue())
    }


    @Test
    fun boundaryValueEmptyMessageIsNull() {
        assertNull(JobMessage.ofPayload(null).boundaryValue())
    }
}
