package tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats.store

import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.stats.ValueStatistics
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals


class FileValueStatisticsStoreTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyWithMissingRow() {
        use(1) { store ->
            val values = store.get(0, listOf(IndexedValue(0, PivotValueType.Count)))
            assert(ValueStatistics.isMissing(values[0]))
        }
    }


    @Test
    fun countOfSingle() {
        use(1) { store ->
            store.addOrUpdate(0, doubleArrayOf(1.0))

            val values = store.get(0, listOf(
                IndexedValue(0, PivotValueType.Count)))

            assertEquals(1.0, values[0])
        }
    }


    @Test
    fun countAndSumOfFew() {
        use(1) { store ->
            store.addOrUpdate(1, doubleArrayOf(10.0))
            store.addOrUpdate(1, doubleArrayOf(15.0))
            store.addOrUpdate(0, doubleArrayOf(42.0))

            val firstValues = store.get(0, listOf(
                IndexedValue(0, PivotValueType.Count),
                IndexedValue(0, PivotValueType.Sum)))

            assertEquals(1.0, firstValues[0])
            assertEquals(42.0, firstValues[1])

            val secondValues = store.get(1, listOf(
                IndexedValue(0, PivotValueType.Count),
                IndexedValue(0, PivotValueType.Sum)))

            assertEquals(2.0, secondValues[0])
            assertEquals(25.0, secondValues[1])
        }
    }


    @Test
    fun sumOfTwoColumns() {
        use(2) { store ->
            store.addOrUpdate(0, doubleArrayOf(10.0, 20.0))

            val values = store.get(0, listOf(
                IndexedValue(0, PivotValueType.Sum),
                IndexedValue(1, PivotValueType.Sum)))

            assertEquals(10.0, values[0])
            assertEquals(20.0, values[1])
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @OptIn(ExperimentalPathApi::class)
    private fun use(valueColumnCount: Int, consumer: (FileValueStatisticsStore) -> Unit) {
        val file = kotlin.io.path.createTempFile("FileValueStatisticsStore")

        try {
            FileValueStatisticsStore(
                file,
                valueColumnCount
            ).use {
                consumer.invoke(it)
            }
        }
        finally {
            Files.delete(file)
        }
    }
}