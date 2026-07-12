package tech.kzen.auto.client.objects.document.script.display.target

import react.ChildrenBuilder
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class XpathTargetTypeDisplay(
    objectLocation: ObjectLocation
): TargetTypeDisplay(objectLocation) {
    override val typeName = "Xpath"
    override val editorLabel = "Matching XPath"


    override fun ChildrenBuilder.renderValueEditor(context: TargetValueEditorContext) {
        textValueEditor(context)
    }


    override fun ChildrenBuilder.renderSummary(context: TargetSummaryContext) {
        summaryText("Matching XPath ${context.value ?: ""}")
    }
}
