package tech.kzen.auto.common.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class DataModelDigestTest {
    @Test
    fun equalValuesHaveEqualDigestsRegardlessOfAttributeOrder() {
        val firstRef = DataRef(null, "/data/main.csv", linkedMapOf("b" to "2", "a" to "1"))
        val secondRef = DataRef(null, "/data/main.csv", linkedMapOf("a" to "1", "b" to "2"))
        val firstUnit = DataUnit(
            linkedMapOf("z" to "last", "a" to "first"),
            listOf(DataPart(DataRole.main, firstRef, null, null))
        )
        val secondUnit = DataUnit(
            linkedMapOf("a" to "first", "z" to "last"),
            listOf(DataPart(DataRole.main, secondRef, null, null))
        )

        assertEquals(firstRef, secondRef)
        assertEquals(firstRef.digest(), secondRef.digest())
        assertEquals(firstUnit, secondUnit)
        assertEquals(firstUnit.digest(), secondUnit.digest())
        assertEquals(
            DataManifest(listOf(firstUnit)).digest(),
            DataManifest(listOf(secondUnit)).digest()
        )
    }


    @Test
    fun semanticChangesChangeDigest() {
        val base = DataRef(null, "/data/main.csv", mapOf("region" to "north"))

        assertNotEquals(base.digest(), base.copy(attributes = mapOf("region" to "south")).digest())
        assertNotEquals(base.digest(), base.copy(source = DataSourceId("provider-1")).digest())
        assertNotEquals(
            base.digest(),
            base.copy(attributes = mapOf(
                "region" to "north",
                DataRef.sizeKey to "123",
                DataRef.modifiedKey to "2026-08-23T10:15:30Z"
            )).digest()
        )
    }
}
