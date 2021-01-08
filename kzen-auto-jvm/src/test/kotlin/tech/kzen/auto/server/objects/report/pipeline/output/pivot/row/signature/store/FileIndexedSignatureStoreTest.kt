package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.store

import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.RowSignature
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals


class FileIndexedSignatureStoreTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun singleEmptySignature() {
        use(0) { store ->
            store.add(RowSignature.of())
            val signature = store.get(0)
            assertEquals(0, signature.valueIndexes.size)
        }
    }


    @Test
    fun singleValueOneRow() {
        use(1) { store ->
            store.add(RowSignature.of(42))
            val signature = store.get(0)
            assertEquals(1, signature.valueIndexes.size)
            assertEquals(42, signature.valueIndexes[0])
        }
    }


    @Test
    fun threeValuesOneRow() {
        use(1) { store ->
            store.add(RowSignature.of(10))
            store.add(RowSignature.of(11))
            store.add(RowSignature.of(12))
            assertEquals(10, store.get(0).valueIndexes[0])
            assertEquals(11, store.get(1).valueIndexes[0])
            assertEquals(12, store.get(2).valueIndexes[0])
        }
    }


    @Test
    fun threeValuesTwoRows() {
        use(2) { store ->
            store.add(RowSignature.of(10, 11))
            store.add(RowSignature.of(12, 13))
            store.add(RowSignature.of(14, 15))
            assertEquals(10, store.get(0).valueIndexes[0])
            assertEquals(11, store.get(0).valueIndexes[1])
            assertEquals(12, store.get(1).valueIndexes[0])
            assertEquals(13, store.get(1).valueIndexes[1])
            assertEquals(14, store.get(2).valueIndexes[0])
            assertEquals(15, store.get(2).valueIndexes[1])
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun use(signatureSize: Int, consumer: (FileIndexedSignatureStore) -> Unit) {
        val file = createTempFile("FileIndexedSignatureStore").toPath()

        try {
            FileIndexedSignatureStore(
                file,
                signatureSize
            ).use {
                consumer.invoke(it)
            }
        }
        finally {
            Files.delete(file)
        }
    }
}