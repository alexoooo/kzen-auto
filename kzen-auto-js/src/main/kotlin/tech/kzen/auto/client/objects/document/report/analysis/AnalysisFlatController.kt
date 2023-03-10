package tech.kzen.auto.client.objects.document.report.analysis

import csstype.*
import emotion.react.css
import js.core.jso
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.react
import tech.kzen.auto.client.objects.document.common.edit.MultiTextAttributeEditor
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.CheckIcon
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisFlatDataSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisFlatControllerProps: Props {
    var mainLocation: ObjectLocation
    var analysisColumnInfo: AnalysisColumnInfo?
    var spec: AnalysisFlatDataSpec
    var reportInputStore: ReportInputStore
    var reportOutputStore: ReportOutputStore
    var runningOrLoading: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisFlatController(
    props: AnalysisFlatControllerProps
):
    RPureComponent<AnalysisFlatControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val editorWidth = 18.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onChangedByEdit() {
        props.reportInputStore.listColumnsAsync()
        props.reportOutputStore.lookupOutputOfflineIfTableAsync()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun PropertiesBuilder.commonThCss() {
        position = Position.sticky
        top = 0.px
        backgroundColor = NamedColor.white
        zIndex = integer(999)
        textAlign = TextAlign.left
        boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-2).px, 0.px, 0.px, NamedColor.lightgray)
        paddingLeft = 0.5.em
        paddingRight = 0.5.em
    }


    private fun PropertiesBuilder.commonTdCss(isFirst: Boolean) {
        paddingLeft = 0.5.em
        paddingRight = 0.5.em

        if (! isFirst) {
            borderTopWidth = 1.px
            borderTopStyle = LineStyle.solid
            borderTopColor = NamedColor.lightgray
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val analysisColumnInfo = props.analysisColumnInfo
            ?: return

        div {
            css {
                width = 100.pct
                marginTop = 0.5.em
            }

            div {
                css {
                    width = 100.pct.minus(editorWidth).minus(1.em)
                    display = Display.inlineBlock
                }
                renderTable(analysisColumnInfo)
            }


            div {
                css {
                    width = editorWidth
                    display = Display.inlineBlock
                    verticalAlign = VerticalAlign.top
                    marginLeft = 1.em
                }

                if (props.runningOrLoading) {
                    title = "Disabled while running"
                }

                renderEditAllow(analysisColumnInfo)
                renderEditExclude(analysisColumnInfo)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderTable(analysisColumnInfo: AnalysisColumnInfo) {
        div {
            css {
                maxHeight = 20.em
                overflowY = Auto.auto
                borderWidth = 2.px
                borderStyle = LineStyle.solid
                borderColor = NamedColor.lightgray
            }

            table {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                thead {
                    tr {
                        th {
                            css {
                                commonThCss()
                            }
                            +"Column Number"
                        }
                        th {
                            css {
                                commonThCss()
                                width = 100.pct
                            }
                            +"Column Name"
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            +"Included"
                        }
                    }
                }

                tbody {
                    for ((index, e) in analysisColumnInfo.inputAndCalculatedColumns.entries.withIndex()) {
                        val included = e.value
                        val isFirst = index == 0

                        tr {
                            key = e.key

                            css {
                                hover {
                                    backgroundColor = NamedColor.lightgrey
                                }
                            }

                            td {
                                css {
                                    commonTdCss(isFirst)
                                }
                                +"${index + 1}"
                            }

                            td {
                                css {
                                    commonTdCss(isFirst)
                                    if (included) {
                                        fontWeight = FontWeight.bold
                                    }
                                }
                                +e.key
                            }

                            td {
                                css {
                                    commonTdCss(isFirst)
                                }
                                if (included) {
                                    CheckIcon::class.react {
                                        style = jso {
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderEditAllow(analysisColumnInfo: AnalysisColumnInfo) {
        div {
//            css {
//                marginTop = 1.em
//            }

            val allowPatternError = analysisColumnInfo.allowPatternError

            MultiTextAttributeEditor::class.react {
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

            if (allowPatternError != null) {
                div {
                    css {
                        color = NamedColor.red
                    }
                    +allowPatternError
                }
            }
        }
    }


    private fun ChildrenBuilder.renderEditExclude(analysisColumnInfo: AnalysisColumnInfo) {
        div {
            css {
                marginTop = 2.em
            }

            val excludePatternError = analysisColumnInfo.excludePatternError

            MultiTextAttributeEditor::class.react {
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

            if (excludePatternError != null) {
                div {
                    css {
                        color = NamedColor.red
                    }
                    +excludePatternError
                }
            }
        }
    }
}