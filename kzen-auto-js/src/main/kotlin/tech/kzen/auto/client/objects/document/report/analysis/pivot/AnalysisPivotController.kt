package tech.kzen.auto.client.objects.document.report.analysis.pivot

import web.cssom.em
import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisPivotControllerProps: Props {
    var spec: PivotSpec
    var inputAndCalculatedColumns: HeaderListing?
    var analysisStore: ReportAnalysisStore
    var runningOrLoading: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisPivotController(
    props: AnalysisPivotControllerProps
):
    RPureComponent<AnalysisPivotControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (props.inputAndCalculatedColumns == null) {
            // TODO: is this good usability?
            return
        }

        div {
            css {
                marginBottom = 1.em
            }
            renderRows()
        }

        renderValues()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRows() {
        AnalysisPivotRowListController::class.react {
            spec = props.spec
            inputAndCalculatedColumns = props.inputAndCalculatedColumns
            analysisStore = props.analysisStore
            runningOrLoading = props.runningOrLoading
        }
    }


    private fun ChildrenBuilder.renderValues() {
        AnalysisPivotValueListController::class.react {
            spec = props.spec
            inputAndCalculatedColumns = props.inputAndCalculatedColumns
            analysisStore = props.analysisStore
            runningOrLoading = props.runningOrLoading
        }
    }
}