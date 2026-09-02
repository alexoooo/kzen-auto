package tech.kzen.auto.common.data.schema

import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataContract


interface RecordSchema {
    fun contract(): DataContract
}


fun RecordSchema.declaredShape(): DataShape = DataShape(
    contract(),
    ShapeProvenance.Declared,
    ShapeStability.Stable,
    emptyList())
