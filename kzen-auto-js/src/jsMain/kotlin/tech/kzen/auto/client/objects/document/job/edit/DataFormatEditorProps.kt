package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps


external interface DataFormatEditorProps: AttributeEditorProps {
    var kind: DataFormatEditor.Kind
}
