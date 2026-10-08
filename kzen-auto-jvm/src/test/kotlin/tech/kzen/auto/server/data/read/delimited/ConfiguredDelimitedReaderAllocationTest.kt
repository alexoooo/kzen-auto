package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.common.data.read.CharacterDecodingSpec
import tech.kzen.auto.common.data.read.DelimitedDialectSpec
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.read.RecordFramingSpec
import tech.kzen.auto.common.data.read.TypedDecodePolicy
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.content.SequentialCharacterContent
import tech.kzen.auto.server.objects.job.worker.data.DataReadCore
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * What Parse allocates per row of a 12-column CSV file (the benchmark's wide rows), from the reader through
 * [DataReadCore]'s message: once into the file's own shape, and once projected into a superset with a column the
 * file lacks. Measured as this thread's allocated bytes over a pass of the whole file; printed for the record.
 */
class ConfiguredDelimitedReaderAllocationTest {
    private val rows = 50_000
    private val rounds = 3

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private val header = "id,flag,cat,qty,price,c5,c6,c7,c8,c9,c10,c11"
    private val text: String = buildString {
        append(header).append('\n')
        repeat(rows) { index ->
            append(index).append(if (index % 2 == 0) ",yes" else ",no")
            append(",cat").append(index % 8).append(',').append(index % 20 + 1).append(',')
            append(index % 9_999 / 100.0)
            for (column in 5..11) {
                append(",v").append(column).append('_').append(index)
            }
            append('\n')
        }
    }

    private val config = DelimitedReadConfig(
        RecordFramingSpec("lf"),
        DelimitedDialectSpec(",", "\"", "double-quote", "empty", "none"),
        HeaderReadSpec("present", "exact-name"),
        CharacterDecodingSpec("UTF-8", "forbid", "report", "report"),
        null,
        TypedDecodePolicy(null, "fail-part", emptyList()))


    @Test
    fun aRowInItsOwnShape() {
        val bytesPerRow = bytesPerRow { cursorShape ->
            // An equal, distinct contract: what a run validated before Run, as Parse holds it
            DataReadCore.ShapeBaseline(shapeOf(DataContract(cursorShape.itemType.structural)), "validated")
        }
        println("Parse allocation, own shape: %.1f bytes/row".format(bytesPerRow))
        assertTrue(bytesPerRow < ownShapeBudget, "%.1f bytes/row".format(bytesPerRow))
    }


    @Test
    fun aRowProjectedIntoASuperset() {
        val bytesPerRow = bytesPerRow { cursorShape ->
            val fields = (cursorShape.itemType.structural as DataType.Record).fields
            val extra = DataField(FieldId("extra"), DataType.Scalar(ScalarKind.Text), optional = true)
            DataReadCore.ShapeBaseline(shapeOf(DataContract(DataType.Record(fields + extra))), "superset")
        }
        println("Parse allocation, superset projection: %.1f bytes/row".format(bytesPerRow))
        assertTrue(bytesPerRow < supersetBudget, "%.1f bytes/row".format(bytesPerRow))
    }


    /** A comment prefix is checked at every row and, when it does not match, given back without allocating. */
    @Test
    fun aCommentPrefixCostsNothingPerRow() {
        val ownShape = { cursorShape: DataShape ->
            DataReadCore.ShapeBaseline(shapeOf(DataContract(cursorShape.itemType.structural)), "validated")
        }
        val withoutPrefix = bytesPerRow(config, ownShape)
        val withPrefix = bytesPerRow(config.copy(commentPrefix = "#!"), ownShape)
        val extra = withPrefix - withoutPrefix
        println("Parse allocation, comment prefix: %.1f bytes/row more".format(extra))
        assertTrue(extra < commentPrefixBudget, "%.1f bytes/row more".format(extra))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A row's copy (the record, its four arrays) and its value are what remains per row, about 600 bytes here;
    // a projection into a superset copies the row once more
    private val ownShapeBudget = 1_000.0
    private val supersetBudget = 2 * ownShapeBudget

    // Less than one object per row
    private val commentPrefixBudget = 8.0


    private fun bytesPerRow(effective: (DataShape) -> DataReadCore.ShapeBaseline): Double =
        bytesPerRow(config, effective)


    private fun bytesPerRow(
        config: DelimitedReadConfig,
        effective: (DataShape) -> DataReadCore.ShapeBaseline
    ): Double {
        if (!threads.isThreadAllocatedMemoryEnabled) {
            threads.isThreadAllocatedMemoryEnabled = true
        }
        repeat(rounds) { pass(config, effective) }
        val allocated = (1..rounds).minOf {
            val before = threads.currentThreadAllocatedBytes
            pass(config, effective)
            threads.currentThreadAllocatedBytes - before
        }
        return allocated.toDouble() / rows
    }


    private fun pass(config: DelimitedReadConfig, effective: (DataShape) -> DataReadCore.ShapeBaseline) {
        val reader = ConfiguredDelimitedReader.open(
            StringContent(text), config, ReadOperationalPolicy(), DelimitedReadContext("memory://wide"))
        reader.use {
            val cursorShape = shapeOf(reader.contract)
            val projection = DataReadCore.projection(null, cursorShape, effective(cursorShape), null)
            var count = 0
            while (true) {
                val record = reader.read() ?: break
                projection.message(record.value)
                count++
            }
            assertEquals(rows, count)
        }
    }


    private fun shapeOf(contract: DataContract) =
        DataShape(contract, ShapeProvenance.Inferred, ShapeStability.Stable)


    private class StringContent(private val text: String): SequentialCharacterContent {
        override val resolvedCharsetName = "UTF-8"
        override val inspectionRecordLimit = Long.MAX_VALUE
        private var position = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position >= text.length) return -1
            val count = minOf(length, text.length - position)
            text.toCharArray(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() {}
    }
}
