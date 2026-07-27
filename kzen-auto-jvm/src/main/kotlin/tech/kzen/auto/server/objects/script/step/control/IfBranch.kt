package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * One branch of an [IfStep] chain — its Kotlin Boolean condition and the steps it guards — as a notation
 * object, so branch identity is an object name and branch ORDER is document position (see IfStep's KDoc for
 * why that shape).
 *
 * Deliberately NOT a [tech.kzen.auto.server.objects.script.api.ScriptStep]: it is never executed and never
 * validated as a step. It exists as a class only so [tech.kzen.lib.common.service.context.GraphCreator] has
 * something to instantiate per branch — [IfStep] itself reads the branch's condition and steps from NOTATION,
 * and owns compiling / evaluating the condition (an unusable one is reported as the If's own validation
 * error, by branch position). Neither injected member is read here.
 */
@Reflect
class IfBranch(
    @Suppress("unused") val condition: String,
    @Suppress("unused") val steps: List<ObjectLocation>
)
