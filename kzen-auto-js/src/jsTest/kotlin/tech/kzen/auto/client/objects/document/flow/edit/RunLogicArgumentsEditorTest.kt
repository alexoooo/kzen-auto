package tech.kzen.auto.client.objects.document.flow.edit

import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals


class RunLogicArgumentsEditorTest {
    @Test
    fun jobRunWorkerShowsOnlyAdditionalInputsInSignatureOrder() {
        val signature = LogicSignature(
            TupleDefinition(listOf("unit", "date", "prefix").map {
                TupleComponentDefinition(TupleComponentName(it), LogicType(TypeMetadata.anyNullable))
            }),
            TupleDefinition.empty)

        assertEquals(listOf("date", "prefix"), jobRunArgumentNames(signature, true))
        assertEquals(listOf("unit", "date", "prefix"), jobRunArgumentNames(signature, false))
    }
}
