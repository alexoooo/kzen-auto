package tech.kzen.auto.server.exec.flow

import tech.kzen.auto.common.paradigm.flow.model.channel.MutableRequiredInput
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame


class FlowTypedChannelTest {
    private data class Reading(val value: Int)


    @Test
    fun nativePortReceivesTheOriginalInstance() {
        val reading = Reading(7)
        val value = DefaultDataAdapterRegistry().lift(reading)
        val input = MutableRequiredInput<Reading>(value.contract, structural = false)

        input.set(value, reading)

        assertSame(reading, input.get())
    }


    @Test
    fun structuralPortReceivesTheTransportValueItself() {
        val value = DefaultDataAdapterRegistry().lift(Reading(7))
        val input = MutableRequiredInput<DataValue>(value.contract, structural = true)

        input.set(value, Reading(7))

        assertSame(value, input.get())
    }


    @Test
    fun requiredPortDistinguishesPresentNullFromMissing() {
        val contract = DataContract(DataType.Dynamic(nullable = true))
        val value = DefaultDataAdapterRegistry().lift(null, contract)
        val input = MutableRequiredInput<Any?>(contract, structural = false)

        input.set(value, null)

        assertNull(input.get())
    }
}
