package tech.kzen.auto.client.objects.document.script.display.target

import react.ChildrenBuilder
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class TextTargetTypeDisplay(
    objectLocation: ObjectLocation
): TargetTypeDisplay(objectLocation) {
    override val typeName = "Text"
    override val editorLabel = "Containing text"


    override fun ChildrenBuilder.renderValueEditor(context: TargetValueEditorContext) {
        textValueEditor(context)
    }


    override fun ChildrenBuilder.renderSummary(context: TargetSummaryContext) {
        summaryText("Containing text \"${context.value ?: ""}\"")
    }
}
