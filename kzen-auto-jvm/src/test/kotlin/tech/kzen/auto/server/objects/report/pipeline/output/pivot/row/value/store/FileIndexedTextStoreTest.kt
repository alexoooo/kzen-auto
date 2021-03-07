package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.store

import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.FileOffsetStore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
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
    @OptIn(ExperimentalPathApi::class)
    private fun use(consumer: (FileIndexedTextStore) -> Unit) {
        val workUtils = kotlin.io.path.createTempDirectory("FileIndexedTextStoreTest")
        val offsetFile = workUtils.resolve("offset")
        val valueFile = workUtils.resolve("value")

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
            Files.walk(workUtils)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete)
        }
    }
}