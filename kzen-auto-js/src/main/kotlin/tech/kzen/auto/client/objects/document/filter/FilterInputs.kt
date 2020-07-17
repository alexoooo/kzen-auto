package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import kotlinx.html.title
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
import tech.kzen.lib.common.model.structure.notation.AttributeNotation


class FilterInputs(
    props: Props
):
    RPureComponent<FilterInputs.Props, FilterInputs.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var mainLocation: ObjectLocation,
        var clientState: SessionState,
        var taskRunning: Boolean,
        var onChange: ((List<String>?) -> Unit),
        var onListing: ((List<String>?) -> Unit)
    ): RProps


    class State(
        var fileListingLoaded: Boolean,
        var fileListingLoading: Boolean,
        var fileListing: List<String>?,
        var error: String?,
        var changedAttributeNotation: AttributeNotation?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var mounted: Boolean = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        fileListingLoaded = false
        fileListingLoading = false
        fileListing = null
        error = null
        changedAttributeNotation = null
    }


    override fun componentDidMount() {
        mounted = true
    }


    override fun componentWillUnmount() {
        mounted = false
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
//        if (! mounted) {
//            console.log("^^^^^^ !!!! FilterInputs componentDidUpdate without mounted!?!?")
//        }

        if (props.mainLocation != prevProps.mainLocation) {
            setState {
                fileListingLoaded = false
                fileListingLoading = false
                fileListing = null
                error = null
                changedAttributeNotation = null
            }
            props.onListing(null)
            return
        }

//        console.log("^6666 FilterInputs - componentDidUpdate")
        if (state.fileListing != prevState.fileListing) {
            if (state.changedAttributeNotation != null) {
                props.onChange(state.fileListing)
            }
            else {
                props.onListing(state.fileListing)
            }
        }

        if (state.error != null) {
//            console.log("&%&^%&%^&% FilterInputs componentDidUpdate - ${state.error}")
            return
        }

        if (state.fileListingLoading) {
            if (! prevState.fileListingLoading) {
//                console.log("^^^^^ getFileListing")
                getFileListing()
            }
            return
        }

        if (state.fileListing == null) {
//            console.log("^^^^^ fileListingLoading = true")
            setState {
                fileListingLoading = true
                fileListingLoaded = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAttributeChanged(attributeNotation: AttributeNotation) {
//        console.log("############## onAttributeChanged - $mounted")
        if (! mounted) {
            return
        }

        setState {
            fileListingLoading = true
            fileListingLoaded = false
            fileListing = null
            error = null
            changedAttributeNotation = attributeNotation
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun getFileListing() {
//        console.log("############## getFileListing - $mounted")
        if (! mounted) {
            return
        }

        setState {
            error = null
        }

        async {
//            console.log("^^^^ getFileListing requesting")
            val result = ClientContext.restClient.performDetached(
                props.mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionListFiles)
//            console.log("^^^^ getFileListing got: $result")

//            console.log("############## getFileListing - result - $mounted")
            if (! mounted) {
                return@async
            }

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as List<String>

//                    console.log("&&&&&& FilterInputs ||| fileListing - $resultValue")
                    setState {
                        fileListing = resultValue
                        error = null
                    }
                }

                is ExecutionFailure -> {
//                    console.log("&&&&&& FilterInputs ||| error - ${result.errorMessage}")
                    setState {
                        fileListing = null
                        error = result.errorMessage
                    }
                }
            }

//            console.log("############## getFileListing - after - $mounted")
            if (! mounted) {
                return@async
            }

            setState {
                fileListingLoaded = true
                fileListingLoading = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val invalid = state.error != null ||
                state.fileListing == null && ! state.fileListingLoaded

        child(MaterialPaper::class) {
            child(MaterialCardContent::class) {
                styledSpan {
                    css {
                        fontSize = 2.em
                    }
                    +"Input"
                }

                renderPattern(invalid)

                renderFiles(invalid)
            }
        }
    }


    private fun RBuilder.renderPattern(invalid: Boolean) {
        styledDiv {
            css {
                marginTop = 0.5.em
            }

            attrs {
                if (props.taskRunning) {
                    title = "Disabled while task running"
                }
            }

            child(DefaultAttributeEditor::class) {
                attrs {
                    clientState = props.clientState
                    objectLocation = props.mainLocation
                    attributeName = FilterConventions.inputAttribute
                    labelOverride = "Pattern"
                    disabled = props.taskRunning
                    onChange = {
                        onAttributeChanged(it)
                    }

                    this.invalid = invalid
                }
            }
        }
    }


    private fun RBuilder.renderFiles(invalid: Boolean) {
        if (invalid) {
            return
        }

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