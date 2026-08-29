package tech.kzen.auto.client.objects.document.flow.edit

import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import kotlin.test.Test
import kotlin.test.assertEquals


class RunLogicArgumentsEditorTest {
    @Test
    fun jobRunWorkerShowsOnlyAdditionalInputsInSignatureOrder() {
        val signature = LogicSignature(
            BindingSchema.of(listOf("unit", "date", "prefix").map {
                BindingDefinition(
                    BindingName(it),
                    DataContract(DataType.Dynamic(nullable = true)))
            }),
            BindingSchema.empty)

        assertEquals(listOf("date", "prefix"), jobRunArgumentNames(signature, true))
        assertEquals(listOf("unit", "date", "prefix"), jobRunArgumentNames(signature, false))
    }
}
