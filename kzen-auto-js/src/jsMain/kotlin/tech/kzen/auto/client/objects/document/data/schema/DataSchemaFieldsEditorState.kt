package tech.kzen.auto.client.objects.document.data.schema

import react.State
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec


external interface DataSchemaFieldsEditorState: State {
    var fields: Map<String, DataSchemaFieldSpec>?
}
