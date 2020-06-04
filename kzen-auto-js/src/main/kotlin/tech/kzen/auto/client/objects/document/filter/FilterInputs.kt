package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import react.*
import react.dom.key
import react.dom.li
import styled.css
import styled.styledDiv
import styled.styledOl
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialCardContent
import tech.kzen.auto.client.wrap.MaterialPaper
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.locate.ObjectLocation


class FilterInputs(
    props: Props
):
    RPureComponent<FilterInputs.Props, FilterInputs.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var mainLocation: ObjectLocation,
        var clientState: SessionState
    ): RProps


    class State(
        var fileListingLoading: Boolean,
        var fileListing: List<String>?,
        var error: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        fileListingLoading = false
        fileListing = null
        error = null
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (state.fileListingLoading && ! prevState.fileListingLoading) {
            getFileListing()
        }

        val fileListing = state.fileListing
        if (fileListing == null && ! state.fileListingLoading) {
            setState {
                fileListingLoading = true
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun getFileListing() {
        async {
            val result = ClientContext.restClient.performDetached(
                props.mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionListFiles)

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as List<String>

                    setState {
                        fileListing = resultValue
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            setState {
                fileListingLoading = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialPaper::class) {
            child(MaterialCardContent::class) {
                styledSpan {
                    css {
                        fontSize = 2.em
                    }
                    +"Input"
                }

                renderPattern()

                renderFiles()
            }
        }
    }


    private fun RBuilder.renderPattern() {
        styledDiv {
            css {
                marginTop = 0.5.em
            }

            child(DefaultAttributeEditor::class) {
                attrs {
                    clientState = props.clientState
                    objectLocation = props.mainLocation
                    attributeName = FilterConventions.inputAttribute
                    labelOverride = "Pattern"
                }
            }
        }
    }


    private fun RBuilder.renderFiles() {
        val error = state.error
        if (error != null) {
            +"Listing error: $error"
            return
        }

        val fileListing = state.fileListing

        styledDiv {
            css {
                maxHeight = 20.em
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

            if (fileListing == null) {
                +"..."
            }
            else {
                styledOl {
                    css {
                        marginTop = 0.px
                        marginBottom = 0.px
                        marginLeft = (-25).px
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