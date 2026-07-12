package tech.kzen.auto.client.objects.document.script.display.target

import react.ChildrenBuilder
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class FocusTargetTypeDisplay(
    objectLocation: ObjectLocation
): TargetTypeDisplay(objectLocation) {
    override val typeName = "Focus"
    override val editorLabel = "Currently focused"
    override val hasValue = false


    override fun ChildrenBuilder.renderSummary(context: TargetSummaryContext) {
        summaryText("Currently focused")
    }
}
