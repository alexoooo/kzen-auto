package tech.kzen.auto.client.objects.document.pipeline.analysis

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import kotlinx.html.title
import react.RBuilder
import react.RPureComponent
import react.dom.attrs
import styled.*
import tech.kzen.auto.client.objects.document.common.edit.MultiTextAttributeEditor
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputStore
import tech.kzen.auto.client.wrap.material.CheckIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisFlatDataSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


class AnalysisFlatController(
    props: Props
):
    RPureComponent<AnalysisFlatController.Props, AnalysisFlatController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val editorWidth = 18.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var mainLocation: ObjectLocation
        var analysisColumnInfo: AnalysisColumnInfo?
        var spec: AnalysisFlatDataSpec
        var pipelineInputStore: PipelineInputStore
        var pipelineOutputStore: PipelineOutputStore
        var runningOrLoading: Boolean
    }


    interface State: react.State


    //-----------------------------------------------------------------------------------------------------------------
    private fun onChangedByEdit() {
        props.pipelineInputStore.listColumnsAsync()
        props.pipelineOutputStore.lookupOutputOfflineIfTableAsync()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun CssBuilder.commonThCss() {
        position = Position.sticky
        top = 0.px
        backgroundColor = Color.white
        zIndex = 999
        textAlign = TextAlign.left
//        boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
        boxShadowInset(Color.lightGray, 0.px, (-2).px, 0.px, 0.px)
        paddingLeft = 0.5.em
        paddingRight = 0.5.em
    }


    private fun CssBuilder.commonTdCss(isFirst: Boolean) {
        paddingLeft = 0.5.em
        paddingRight = 0.5.em

        if (! isFirst) {
            borderTopWidth = 1.px
            borderTopStyle = BorderStyle.solid
            borderTopColor = Color.lightGray
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val analysisColumnInfo = props.analysisColumnInfo
            ?: return

        styledDiv {
            css {
                width = 100.pct
                marginTop = 0.5.em
            }

            styledDiv {
                css {
                    width = 100.pct.minus(editorWidth).minus(1.em)
                    display = Display.inlineBlock
                }
                renderTable(analysisColumnInfo)
            }


            styledDiv {
                css {
                    width = editorWidth
                    display = Display.inlineBlock
                    verticalAlign = VerticalAlign.top
                    marginLeft = 1.em
                }

                if (props.runningOrLoading) {
                    attrs {
                        title = "Disabled while running"
                    }
                }

                renderEditAllow(analysisColumnInfo)
                renderEditExclude(analysisColumnInfo)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderTable(analysisColumnInfo: AnalysisColumnInfo) {
        styledDiv {
            css {
                maxHeight = 20.em
                overflowY = Overflow.auto
                borderWidth = 2.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                styledThead {
                    styledTr {
                        styledTh {
                            css {
                                commonThCss()
                            }
                            +"Column Number"
                        }
                        styledTh {
                            css {
                                commonThCss()
                                width = 100.pct
                            }
                            +"Column Name"
                        }
                        styledTh {
                            css {
                                commonThCss()
                            }
                            +"Included"
                        }
                    }
                }

                styledTbody {
                    for ((index, e) in analysisColumnInfo.inputAndCalculatedColumns.entries.withIndex()) {
                        val included = e.value
                        val isFirst = index == 0

                        styledTr {
                            key = e.key

                            css {
                                hover {
                                    backgroundColor = Color.lightGrey
                                }
                            }

                            styledTd {
                                css {
                                    commonTdCss(isFirst)
                                }
                                +"${index + 1}"
                            }

                            styledTd {
                                css {
                                    commonTdCss(isFirst)
                                    if (included) {
                                        fontWeight = FontWeight.bold
                                    }
                                }
                                +e.key
                            }

                            styledTd {
                                css {
                                    commonTdCss(isFirst)
                                }
                                if (included) {
                                    child(CheckIcon::class) {
                                        attrs {
                                            style = reactStyle {
                                                marginTop = (-0.2).em
                                                marginBottom = (-0.2).em
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderEditAllow(analysisColumnInfo: AnalysisColumnInfo) {
        styledDiv {
//            css {
//                marginTop = 1.em
//            }

            val allowPatternError = analysisColumnInfo.allowPatternError

            child(MultiTextAttributeEditor::class) {
                attrs {
                    labelOverride = "Allow Patterns"
                    maxRows = 5

                    objectLocation = props.mainLocation
                    attributePath = AnalysisFlatDataSpec.allowAttributePath

                    value = props.spec.allowPatterns
                    unique = true

                    onChange = {
                        onChangedByEdit()
                    }

                    invalid = allowPatternError != null
                    disabled = props.runningOrLoading
                }
            }

            if (allowPatternError != null) {
                styledDiv {
                    css {
                        color = Color.red
                    }
                    +allowPatternError
                }
            }
        }
    }


    private fun RBuilder.renderEditExclude(analysisColumnInfo: AnalysisColumnInfo) {
        styledDiv {
            css {
                marginTop = 2.em
            }

            val excludePatternError = analysisColumnInfo.excludePatternError

            child(MultiTextAttributeEditor::class) {
                attrs {
                    labelOverride = "Exclude Patterns"
                    maxRows = 5

                    objectLocation = props.mainLocation
                    attributePath = AnalysisFlatDataSpec.excludeAttributePath

                    value = props.spec.excludePatterns
                    unique = true

                    onChange = {
                        onChangedByEdit()
                    }

                    invalid = excludePatternError != null
                    disabled = props.runningOrLoading
                }
            }

            if (excludePatternError != null) {
                styledDiv {
                    css {
                        color = Color.red
                    }
                    +excludePatternError
                }
            }
        }
    }
}