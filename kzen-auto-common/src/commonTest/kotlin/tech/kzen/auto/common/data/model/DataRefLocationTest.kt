package tech.kzen.auto.common.data.model

import tech.kzen.auto.common.util.data.DataLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class DataRefLocationTest {
    @Test
    fun plainLocationRoundTrips() {
        val location = DataLocation.of("/data/main.csv")
        val ref = DataRef.ofLocation(location)

        assertEquals(location.asString(), ref.id)
        assertEquals(location, ref.asLocationOrNull())
        assertEquals(ref.id, ref.display())
    }


    @Test
    fun sourcedReferenceIsNotALocation() {
        val ref = DataRef(DataSourceId("provider-1"), "/path-shaped-id")

        assertNull(ref.asLocationOrNull())
        assertNull(DataRef(null, "").asLocationOrNull())
    }


    @Test
    fun fingerprintRequiresBothReservedValues() {
        assertNull(DataRef(null, "/data/main.csv").fingerprintOrNull())
        assertNull(DataRef(null, "/data/main.csv", mapOf(DataRef.sizeKey to "12")).fingerprintOrNull())
        assertNull(DataRef(
            null,
            "/data/main.csv",
            mapOf(DataRef.modifiedKey to "2026-08-23T10:15:30Z")
        ).fingerprintOrNull())
        assertEquals(
            "12" to "2026-08-23T10:15:30Z",
            DataRef(
                null,
                "/data/main.csv",
                mapOf(
                    DataRef.sizeKey to "12",
                    DataRef.modifiedKey to "2026-08-23T10:15:30Z"
                )
            ).fingerprintOrNull()
        )
    }
}
