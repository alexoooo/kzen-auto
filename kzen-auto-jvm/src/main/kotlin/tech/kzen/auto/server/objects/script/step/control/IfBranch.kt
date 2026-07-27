package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * One branch of an [IfStep] chain — its condition and the steps it guards — as a notation object, so branch
 * identity is an object name and branch ORDER is document position (see IfStep's KDoc for why that shape).
 *
 * Deliberately NOT a [tech.kzen.auto.server.objects.script.api.ScriptStep]: it is never executed and never
 * validated as a step. It exists as a class only so [tech.kzen.lib.common.service.context.GraphCreator]
 * succeeds over a configured branch — [IfStep] itself reads the branch's condition and steps from NOTATION,
 * so a branch whose condition is unset merely fails to define its own instance (the If and the document are
 * unaffected, and IfStep.definition reports the unset condition as the If's validation error).
 */
@Reflect
class IfBranch(
    @Suppress("unused") val condition: ObjectLocation,
    @Suppress("unused") val steps: List<ObjectLocation>
)
