package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.exec.data.type.DataType


/**
 * One entry bound against the upstream contract: its resolved [steps], the scalar [leaf] type and the
 * [outputName] it projects to. [iterationKey] is the prefix through the last unnesting step — paths sharing
 * it iterate the same list / map together; distinct keys form a cross product (a nested key depends on its
 * parent's element).
 */
data class BoundPath(
    val entry: PathProjectionEntry,
    val steps: List<BoundStep>,
    val leaf: DataType.Scalar
) {
    val outputName: String
        get() = entry.outputName


    val iterationKey: List<BoundStep>
        get() {
            val last = steps.indexOfLast { it.unnests }
            return if (last < 0) emptyList() else steps.subList(0, last + 1)
        }
}
