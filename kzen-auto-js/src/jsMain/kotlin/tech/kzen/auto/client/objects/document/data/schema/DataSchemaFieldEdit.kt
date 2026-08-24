package tech.kzen.auto.client.objects.document.data.schema

import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


//---------------------------------------------------------------------------------------------------------------------
external interface DataSchemaFieldEditProps: Props {
    var objectLocation: ObjectLocation
    var fieldName: String
    var fieldSpec: DataSchemaFieldSpec
}


external interface DataSchemaFieldEditState: State {
//    var newFieldName: String
//    var adding: Boolean
//    var previousError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class DataSchemaFieldEdit(
    props: DataSchemaFieldEditProps
):
    RPureComponent<DataSchemaFieldEditProps, DataSchemaFieldEditState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun DataSchemaFieldEditState.init(props: DataSchemaFieldEditProps) {
//        newFieldName = ""
//        adding = false
//        previousError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            +"Name: ${props.fieldName}"
        }

        renderMetadata(props.fieldSpec.typeMetadata)
    }


    private fun ChildrenBuilder.renderMetadata(typeMetadata: TypeMetadata) {
        div {
            +"ClassName: ${typeMetadata.className}"
        }
        div {
            +"Nullable: ${typeMetadata.nullable}"
        }
        div {
            +"Generics:"
            for ((index, genericType) in typeMetadata.generics.withIndex()) {
                div {
                    key = Key(index.toString())
                    renderMetadata(genericType)
                }
            }
        }
    }
}
