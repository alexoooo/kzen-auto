package tech.kzen.auto.common.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class DataUnitTest {
    private val mainFirst = DataPart.ofPath(DataRole.main, "/data/main-1.csv")
    private val reference = DataPart.ofPath(DataRole("reference"), "/data/reference.csv")
    private val mainSecond = DataPart.ofPath(DataRole.main, "/data/main-2.csv")


    @Test
    fun partsOfFiltersInOrder() {
        val unit = DataUnit(emptyMap(), listOf(mainFirst, reference, mainSecond))

        assertEquals(listOf(mainFirst, mainSecond), unit.partsOf(DataRole.main))
        assertEquals(reference, unit.part(DataRole("reference")))
    }


    @Test
    fun partFailsWithRoleAndCount() {
        val unit = DataUnit(emptyMap(), listOf(mainFirst, mainSecond))

        val missing = assertFailsWith<IllegalStateException> {
            unit.part(DataRole("missing"))
        }
        assertTrue(missing.message.orEmpty().contains("missing"))
        assertTrue(missing.message.orEmpty().contains("0"))

        val duplicate = assertFailsWith<IllegalStateException> {
            unit.part(DataRole.main)
        }
        assertTrue(duplicate.message.orEmpty().contains(DataRole.main.name))
        assertTrue(duplicate.message.orEmpty().contains("2"))
    }


    @Test
    fun singleRoleRequiresAtLeastOnePart() {
        assertFalse(DataUnit(emptyMap(), emptyList()).isSingleRole)
        assertTrue(DataUnit(emptyMap(), listOf(mainFirst, mainSecond)).isSingleRole)
        assertFalse(DataUnit(emptyMap(), listOf(mainFirst, reference)).isSingleRole)
    }


    @Test
    fun constructionHelpersMatchConstructors() {
        val explicitMainPart = DataPart(
            DataRole.main,
            DataRef(null, "/data/main.csv"),
            null,
            null
        )
        assertEquals(
            explicitMainPart,
            DataPart.ofPath(DataRole.main, "/data/main.csv")
        )

        assertEquals(
            DataUnit(emptyMap(), listOf(mainFirst, reference)),
            DataUnit.of(mainFirst, reference)
        )

        val attributes = linkedMapOf("date" to "2026-08-23")
        assertEquals(
            DataUnit(attributes, listOf(mainFirst, reference)),
            DataUnit.of(attributes, listOf(mainFirst, reference))
        )

        assertEquals(
            DataUnit(emptyMap(), listOf(explicitMainPart)),
            DataUnit.ofPath("/data/main.csv")
        )
    }
}
