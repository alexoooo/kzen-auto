package tech.kzen.auto.server.objects.job.worker.data

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.job.worker.JobMessage
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class LogicSourceWorkerTest {
    private val workerLocation = ObjectLocation.parse("test/logic-source-worker.yaml#main.workers/logic")
    private val instructions = ObjectLocation.parse("test/child.yaml#main")


    @Test
    fun directlyOwnedLogicSourceHostsAndEmitsUnits() = runBlocking {
        val messages = mutableListOf<JobMessage>()
        val control = HostingControl()
        LogicSourceWorker(
            capturing(messages), instructions, listOf("date"), null,
            ReadWorker.emitUnits, "ignored", "ignored", workerLocation,
            DataOpenerLookup(UnusedOpener))
            .run(control)

        assertEquals(listOf(DataUnit.ofPath("result.csv")), messages.map { it.payload })
        assertEquals(instructions, control.instructions)
        assertEquals("2026-08-24", control.input)
    }


    @Test
    fun compatibilityKeyCoversLocationArgumentOrderAndSchemaShape() {
        val base = LogicSourceWorker.compatibilityKey(instructions, listOf("a", "b"), null)
        assertNotEquals(
            base,
            LogicSourceWorker.compatibilityKey(
                ObjectLocation.parse("test/other.yaml#main"), listOf("a", "b"), null))
        assertNotEquals(base, LogicSourceWorker.compatibilityKey(instructions, listOf("b", "a"), null))
        assertNotEquals(
            base,
            LogicSourceWorker.compatibilityKey(
                instructions, listOf("a", "b"),
                DataSchemaDocument(DataSchemaFieldListSpec(
                    linkedMapOf("value" to DataSchemaFieldSpec(TypeMetadata.string))))))
    }


    private fun capturing(messages: MutableList<JobMessage>): ChannelOutput<Any?> {
        return object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                messages.add(element as JobMessage)
            }
            override suspend fun flush() {}
            override fun batchSize(): Int = 1024
            override fun close() {}
        }
    }


    private object UnusedOpener: DataOpener {
        override suspend fun open(context: tech.kzen.auto.common.data.api.DataContext, part: DataPart): DataCursor =
            error("Unit mode does not open parts")
    }


    private class HostingControl: JobControl {
        var instructions: ObjectLocation? = null
        var input: Any? = null


        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("Logic reader needs no scratch directory")
        override fun parameter(name: String): Any? = "2026-08-24"
        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?): TupleValue {
            this.instructions = instructions
            this.input = input
            return TupleValue.ofMain(listOf(DataUnit.ofPath("result.csv")))
        }
    }
}
