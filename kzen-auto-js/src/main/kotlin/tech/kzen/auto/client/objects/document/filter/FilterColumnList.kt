package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.marginBottom
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialCardContent
import tech.kzen.auto.client.wrap.MaterialPaper
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation


class FilterColumnList(
    props: Props
):
    RPureComponent<FilterColumnList.Props, FilterColumnList.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var mainLocation: ObjectLocation,
        var clientState: SessionState,
        var tableSummary: TableSummary?,
        var inputListing: List<String>?,
        var filterRunning: Boolean
    ): RProps


    class State(
        var columnListingLoading: Boolean,
        var columnListing: List<String>?,
        var error: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        columnListingLoading = false
        columnListing = null
        error = null
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
//        console.log("^^^^^ FilterColumnList - componentDidUpdate - " +
//                "${props.mainLocation} / ${prevProps.mainLocation}")

        if (props.mainLocation != prevProps.mainLocation ||
                props.inputListing != prevProps.inputListing) {
//            console.log("############# FilterColumnList reset for new location")
            setState {
                columnListingLoading = true
                columnListing = null
                error = null
            }
            return
        }

        if (state.columnListingLoading && ! prevState.columnListingLoading) {
            getColumnListing()
        }

        val columnListing = state.columnListing
        if (! state.columnListingLoading &&
                columnListing == null && state.error == null
        ) {
            setState {
                columnListingLoading = true
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun getColumnListing() {
        async {
            val result = ClientContext.restClient.performDetached(
                props.mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionListColumns)

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as List<String>

                    setState {
                        columnListing = resultValue
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            setState {
                columnListingLoading = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val columnListing = state.columnListing

        child(MaterialPaper::class) {
            child(MaterialCardContent::class) {
                styledSpan {
                    css {
                        fontSize = 2.em
                    }
                    +"Filters"
                }

                if (columnListing == null) {
                    +"..."
                }
                else {
                    renderColumns(columnListing)
                }
            }
        }
    }


    private fun RBuilder.renderColumns(
        columnListing: List<String>
    ) {
        val criteriaDefinition = props
            .clientState
            .graphDefinitionAttempt
            .successful
            .objectDefinitions[props.mainLocation]!!
            .attributeDefinitions[FilterConventions.criteriaAttributeName]!!
        val criteriaSpec = (criteriaDefinition as ValueAttributeDefinition).value as CriteriaSpec

//        val columnDetails = state.columnDetails

        val tableSummary = props.tableSummary ?: TableSummary.empty
//        val tableSummary = TableSummary.empty

        for (index in columnListing.indices) {
            styledDiv {
                key = index.toString()

                css {
                    marginBottom = 1.em
                }

//                hr {}

                val columnName = columnListing[index]
                child(FilterColumn::class) {
                    attrs {
                        this.mainLocation = props.mainLocation
                        this.clientState = props.clientState
                        this.requiredValues = criteriaSpec.columnRequiredValues[columnName]

                        columnIndex = index
                        this.columnName = columnName

                        columnSummary = tableSummary.columnSummaries[columnName]

                        filterRunning = props.filterRunning
                    }
                }
            }
        }
    }
}