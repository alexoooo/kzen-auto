package tech.kzen.auto.client.objects.document.report.analysis.pivot
//
//import kotlinx.css.em
//import kotlinx.css.marginBottom
//import react.Props
//import react.RBuilder
//import react.RPureComponent
//import styled.css
//import styled.styledDiv
//import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
//import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
//import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
//
//
////---------------------------------------------------------------------------------------------------------------------
//interface AnalysisPivotControllerProps: Props {
//    var spec: PivotSpec
//    var inputAndCalculatedColumns: HeaderListing?
//    var analysisStore: ReportAnalysisStore
//    var runningOrLoading: Boolean
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class AnalysisPivotController(
//    props: AnalysisPivotControllerProps
//):
//    RPureComponent<AnalysisPivotControllerProps, react.State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        if (props.inputAndCalculatedColumns == null) {
//            // TODO: is this good usability?
//            return
//        }
//
//        styledDiv {
//            css {
//                marginBottom = 1.em
//            }
//            renderRows()
//        }
//
//        renderValues()
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderRows() {
//        child(AnalysisPivotRowListController::class) {
//            attrs {
//                spec = props.spec
//                inputAndCalculatedColumns = props.inputAndCalculatedColumns
//                analysisStore = props.analysisStore
//                runningOrLoading = props.runningOrLoading
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderValues() {
//        child(AnalysisPivotValueListController::class) {
//            attrs {
//                spec = props.spec
//                inputAndCalculatedColumns = props.inputAndCalculatedColumns
//                analysisStore = props.analysisStore
//                runningOrLoading = props.runningOrLoading
//            }
//        }
//    }
//}