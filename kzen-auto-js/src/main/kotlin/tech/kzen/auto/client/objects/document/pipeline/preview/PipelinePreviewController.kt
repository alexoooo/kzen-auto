package tech.kzen.auto.client.objects.document.pipeline.preview

import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.edit.BooleanAttributeEditor
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.wrap.material.VisibilityIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


class PipelinePreviewController(
    props: Props
):
    RPureComponent<PipelinePreviewController.Props, PipelinePreviewController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var previewSpec: PreviewSpec
        var runningOrLoading: Boolean
        var afterFilter: Boolean
        var mainLocation: ObjectLocation
    }


    interface State: react.State


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
                marginTop = 5.px
            }

            styledDiv {
                css {
                    borderRadius = 3.px
                    backgroundColor = Color.white
                    width = 100.pct
                }

                styledDiv {
                    css {
                        padding(0.5.em)
                    }

                    renderContent()
                }
            }

            child(ReportBottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()
        renderPreview()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            styledSpan {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                child(VisibilityIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
                            top = (-17).px
                            left = (-3).px
                        }
                    }
                }
            }

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Preview"
            }

            styledSpan {
                css {
                    float = Float.right
                }

                renderEnable()
            }
        }
    }


    private fun RBuilder.renderEnable() {
        child(BooleanAttributeEditor::class) {
            attrs {
                trueLabelOverride = "Enabled"
                falseLabelOverride = "Disabled"

                objectLocation = props.mainLocation
                attributePath = PreviewSpec.enabledAttributePath(props.afterFilter)

                value = props.previewSpec.enabled
                disabled = props.runningOrLoading
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview() {
        styledDiv {
            //child(InfoIcon::class) {}

            styledSpan {
                css {
                    fontSize = 1.25.em
                    fontStyle = FontStyle.italic
                }
                +"Must be enabled for suggestions to appear in Filter"
            }
        }
    }
}