package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.CardContent
import mui.material.Paper
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomDocumentObjectProps: Props {
    var objectPath: ObjectPath
    var objectLocation: ObjectLocation
    var objectMetadata: ObjectMetadata?
    var isAbstract: Boolean
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomDocumentObjectState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomDocumentObject(
    props: CustomDocumentObjectProps
):
    RPureComponent<CustomDocumentObjectProps, CustomDocumentObjectState>(props)
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
            }

            CardContent {
                div {
                    css {
                        fontWeight = FontWeight.bold
                        fontSize = 1.1.em
                        marginBottom = 0.75.em
                    }
                    +props.objectPath.name.value

                    if (props.isAbstract) {
                        span {
                            css {
                                marginLeft = 0.5.em
                                fontWeight = FontWeight.normal
                                fontStyle = FontStyle.italic
                                fontSize = 0.85.em
                                color = Color("rgb(90, 110, 150)")
                            }
                            +"(abstract)"
                        }
                    }
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
            if (AutoConventions.isManaged(attributeName)) {
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
