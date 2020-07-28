package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.key
import react.dom.li
import styled.css
import styled.styledDiv
import styled.styledOl
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
import tech.kzen.auto.client.objects.document.graph.edge.BottomEgress
import tech.kzen.auto.client.objects.document.process.state.ListInputsRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.InputIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.lib.common.model.structure.notation.AttributeNotation


class ProcessInput(
    props: Props
):
    RPureComponent<ProcessInput.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


    class State: RState


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAttributeChanged(attributeNotation: AttributeNotation) {
//        console.log("############## onAttributeChanged - $attributeNotation")
        props.dispatcher.dispatch(ListInputsRequest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
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

            child(BottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()
        renderFilePath()

        val fileListingError = props.processState.fileListingError
        if (fileListingError != null) {
            renderError(fileListingError)
        }
        else {
            renderFileListing()

            val columnListingError = props.processState.columnListingError
            if (columnListingError != null) {
                renderError(columnListingError)
            }
            else {
                renderColumnListing()
            }
        }
    }


    private fun RBuilder.renderHeader() {
        styledDiv {
            child(InputIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.75.em
                        marginRight = 0.25.em
                    }
                }
            }

            styledSpan {
                css {
                    fontSize = 2.em
                }

                +"Input"
            }
        }
    }


    private fun RBuilder.renderFilePath() {
        styledDiv {
            css {
                marginTop = 0.5.em
            }

            attrs {
                if (props.processState.taskRunning()) {
                    title = "Disabled while task running"
                }
            }

            child(DefaultAttributeEditor::class) {
                attrs {
                    clientState = props.processState.clientState
                    objectLocation = props.processState.mainLocation
                    attributeName = FilterConventions.inputAttribute
                    labelOverride = "File Path"
                    disabled = props.processState.taskRunning()
                    onChange = {
                        onAttributeChanged(it)
                    }

//                    this.invalid = invalid
                }
            }
        }
    }


    private fun RBuilder.renderError(message: String) {
        +"Error: $message"
    }


    private fun RBuilder.renderFileListing() {
        val fileListing = props.processState.fileListing

        styledDiv {
            css {
                maxHeight = 15.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
            }

            styledSpan {
                css {
                    color = Color("rgba(0, 0, 0, 0.54)")
                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
                    fontWeight = FontWeight.w400
                    fontSize = 13.px
                }
                +"Files"
            }

            when {
                fileListing == null -> {
                    styledDiv {
                        +"Loading..."
                    }
                }

                fileListing.isEmpty() -> {
                    styledDiv {
                        +"Not found"
                    }
                }

                else -> {
                    styledOl {
                        css {
                            marginTop = 0.px
                            marginBottom = 0.px
                            marginLeft = (-15).px
                        }

                        for (filePath in fileListing) {
                            li {
                                attrs {
                                    key = filePath
                                }

                                styledSpan {
                                    css {
                                        fontFamily = "monospace"
                                    }

                                    +filePath
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderColumnListing() {
        val columnListing = props.processState.columnListing

        styledDiv {
            css {
                maxHeight = 10.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
            }

            styledSpan {
                css {
                    color = Color("rgba(0, 0, 0, 0.54)")
                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
                    fontWeight = FontWeight.w400
                    fontSize = 13.px
                }
                +"Columns"
            }

            when {
                columnListing == null -> {
                    styledDiv {
                        +"Loading..."
                    }
                }

                columnListing.isEmpty() -> {
                    styledDiv {
                        +"None"
                    }
                }

                else -> {
                    styledDiv {
                        css {
                            backgroundColor = Color.lightGray
                        }

                        for ((index, columnName) in columnListing.withIndex()) {
                            styledSpan {
                                key = "$index|$columnName"

                                css {
                                    whiteSpace = WhiteSpace.nowrap
                                    backgroundColor = Color.whiteSmoke
                                    margin(5.px)
                                }

                                +"${index + 1}) "
                                +columnName
                            }
                        }
                    }
                }
            }
        }
    }
}