package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.CardContent
import mui.material.Paper
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectProps: Props {
    var objectPath: ObjectPath
    var objectLocation: ObjectLocation
    var objectMetadata: ObjectMetadata?
    var isAbstract: Boolean
    var isLogic: Boolean
    var isExported: Boolean
    var onToggleExport: (() -> Unit)?
    var onDelete: () -> Unit
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomObject(
    props: CustomObjectProps
):
    RPureComponent<CustomObjectProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        Paper {
            sx {
                if (props.isAbstract) {
                    backgroundColor = Color("rgb(240, 244, 250)")
                    borderStyle = LineStyle.dashed
                    borderWidth = 1.px
                    borderColor = Color("rgb(160, 175, 200)")
                }
                else {
                    backgroundColor = NamedColor.white
                }

                if (props.isExported) {
                    filter = dropShadow(0.px, 0.px, 4.px, Color("rgba(255, 193, 7, 0.55)"))
                }
            }

            CardContent {
                CustomObjectHeader::class.react {
                    objectPath = props.objectPath
                    objectLocation = props.objectLocation
                    isAbstract = props.isAbstract
                    isLogic = props.isLogic
                    isExported = props.isExported
                    onToggleExport = props.onToggleExport
                    onDelete = props.onDelete
                }

                val objectMetadata = props.objectMetadata
                if (objectMetadata == null) {
                    div {
                        css {
                            fontStyle = FontStyle.italic
                            color = Color("rgb(128, 80, 0)")
                        }
                        +"(metadata unavailable)"
                    }
                }
                else {
                    renderAttributes(objectMetadata)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderAttributes(objectMetadata: ObjectMetadata) {
        for (entry in objectMetadata.attributes.map) {
            val attributeName = entry.key
            if (CustomConventions.isManaged(attributeName)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.5.em
                }

                props.attributeEditorManager.child(this) {
                    this.objectLocation = props.objectLocation
                    this.attributeName = attributeName
                }
            }
        }
    }
}
