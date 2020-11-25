package tech.kzen.auto.server.objects.report.pivot.row.value.store

import tech.kzen.auto.server.objects.report.pivot.store.FileOffsetStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals


class FileOffsetStoreTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyHasZeroSize() {
        use { store ->
            assertEquals(0, store.endOffset())
        }
    }


    @Test
    fun addZeroSizeStillZero() {
        use { store ->
            store.add(0)
            assertEquals(0, store.endOffset())
            val span = store.get(0)
            assertEquals(0, span.offset)
            assertEquals(0, span.length)
        }
    }


    @Test
    fun addSingleIncreasesOffset() {
        use { store ->
            store.add(42)
            assertEquals(42, store.endOffset())
            val span = store.get(0)
            assertEquals(0, span.offset)
            assertEquals(42, span.length)
        }
    }


    @Test
    fun addSecondIncreasesOffset() {
        use { store ->
            store.add(10)
            store.add(20)
            assertEquals(30, store.endOffset())

            val first = store.get(0)
            assertEquals(0, first.offset)
            assertEquals(10, first.length)

            val second = store.get(1)
            assertEquals(10, second.offset)
            assertEquals(20, second.length)
        }
    }


    @Test
    fun addSecondThirdIncreasesOffset() {
        use { store ->
            store.add(10)
            store.add(20)
            store.add(30)
            assertEquals(60, store.endOffset())

            val first = store.get(0)
            assertEquals(0, first.offset)
            assertEquals(10, first.length)

            val second = store.get(1)
            assertEquals(10, second.offset)
            assertEquals(20, second.length)

            val third = store.get(2)
            assertEquals(30, third.offset)
            assertEquals(30, third.length)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun use(consumer: (FileOffsetStore) -> Unit) {
        val file = createTempFile("FileIndexedStoreOffsetTest").toPath()

        try {
            FileOffsetStore(file).use {
                consumer.invoke(it)
            }
        }
        finally {
            Files.delete(file)
        }
    }
}