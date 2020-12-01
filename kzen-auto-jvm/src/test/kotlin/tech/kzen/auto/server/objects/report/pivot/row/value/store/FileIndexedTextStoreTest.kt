package tech.kzen.auto.server.objects.report.pivot.row.value.store

import tech.kzen.auto.server.objects.report.pivot.store.FileOffsetStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals


class FileIndexedTextStoreTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun singleValue() {
        use { store ->
            store.add("foo")
            val value = store.get(0)
            assertEquals("foo", value)
        }
    }


    @Test
    fun twoValues() {
        use { store ->
            store.add("foo")
            store.add("bar")
            assertEquals("foo", store.get(0))
            assertEquals("bar", store.get(1))
        }
    }


    @Test
    fun threeValues() {
        use { store ->
            store.add("foo")
            store.add("bar")
            store.add("baz")
            assertEquals("foo", store.get(0))
            assertEquals("bar", store.get(1))
            assertEquals("baz", store.get(2))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun use(consumer: (FileIndexedTextStore) -> Unit) {
        val offsetFile = createTempFile("FileIndexedTextStoreTest-offset").toPath()
        val valueFile = createTempFile("FileIndexedTextStoreTest-value").toPath()

        try {
            FileOffsetStore(
                offsetFile
            ).use { offsetStore ->
                FileIndexedTextStore(
                    valueFile,
                    offsetStore
                ).use {
                    consumer.invoke(it)
                }
            }
        }
        finally {
            Files.delete(valueFile)
            Files.delete(offsetFile)
        }
    }
}