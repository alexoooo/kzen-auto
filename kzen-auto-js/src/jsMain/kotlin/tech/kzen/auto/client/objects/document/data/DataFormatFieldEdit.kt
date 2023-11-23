package tech.kzen.auto.client.objects.document.data

import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.data.spec.FieldFormatSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


//---------------------------------------------------------------------------------------------------------------------
external interface DataFormatFieldEditProps: Props {
    var objectLocation: ObjectLocation
    var fieldName: String
    var fieldSpec: FieldFormatSpec
}


external interface DataFormatFieldEditState: State {
//    var newFieldName: String
//    var adding: Boolean
//    var previousError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class DataFormatFieldEdit(
    props: DataFormatFieldEditProps
):
    RPureComponent<DataFormatFieldEditProps, DataFormatFieldEditState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun DataFormatFieldEditState.init(props: DataFormatFieldEditProps) {
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
                    key = index.toString()
                    renderMetadata(genericType)
                }
            }
        }
    }
}