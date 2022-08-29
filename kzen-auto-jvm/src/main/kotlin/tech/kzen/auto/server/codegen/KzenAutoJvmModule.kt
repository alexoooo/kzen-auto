// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2022-08-28T16:32:06.147179
package tech.kzen.auto.server.codegen

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.auto.server.objects.feature.ScreenshotCropper
import tech.kzen.auto.server.objects.feature.ScreenshotTaker
import tech.kzen.auto.server.objects.graph.AccumulateSink
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput
import tech.kzen.auto.server.objects.graph.AppendText
import tech.kzen.auto.common.paradigm.dataflow.api.input.OptionalInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.RequiredOutput
import tech.kzen.auto.server.objects.graph.CountSink
import tech.kzen.auto.server.objects.graph.DivisibleFilter
import tech.kzen.auto.common.paradigm.dataflow.api.output.OptionalOutput
import tech.kzen.auto.server.objects.graph.IntRangeSource
import tech.kzen.auto.common.paradigm.dataflow.api.output.StreamOutput
import tech.kzen.auto.server.objects.graph.PrimeFilter
import tech.kzen.auto.server.objects.graph.RepeatProcessor
import tech.kzen.auto.common.paradigm.dataflow.api.output.BatchOutput
import tech.kzen.auto.server.objects.graph.ReplaceProcessor
import tech.kzen.auto.server.objects.graph.SelectLast
import tech.kzen.auto.server.objects.logic.LogicTraceStore
import tech.kzen.auto.server.objects.plugin.PluginDocument
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.auto.server.objects.report.ReportDocument
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.server.objects.script.browser.CloseBrowser
import tech.kzen.auto.server.objects.script.browser.FocusElement
import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.server.objects.script.browser.GoTo
import tech.kzen.auto.server.objects.script.browser.OpenBrowser
import tech.kzen.auto.server.objects.script.browser.SendEscape
import tech.kzen.auto.server.objects.script.browser.VisualClick
import tech.kzen.auto.server.objects.script.browser.VisualFormSubmit
import tech.kzen.auto.server.objects.script.browser.VisualSendKeys
import tech.kzen.auto.server.objects.script.DisplayValue
import tech.kzen.auto.server.objects.script.logic.BooleanLiteral
import tech.kzen.auto.server.objects.script.logic.DivisibleCheck
import tech.kzen.auto.server.objects.script.logic.LogicalAnd
import tech.kzen.auto.server.objects.script.logic.LogicalNot
import tech.kzen.auto.server.objects.script.NumberLiteral
import tech.kzen.auto.server.objects.script.NumberRange
import tech.kzen.auto.server.objects.script.TextLiteral
import tech.kzen.auto.server.objects.sequence.SequenceDocument
import tech.kzen.auto.server.objects.sequence.step.BooleanLiteralStep
import tech.kzen.auto.server.objects.sequence.step.DisplayValueStep
import tech.kzen.auto.server.objects.sequence.step.InvokeSequenceStep
import tech.kzen.auto.server.objects.sequence.step.MultiSequenceStep


@Suppress("UNCHECKED_CAST")
object KzenAutoJvmModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
reflectionRegistry.put(
    "tech.kzen.auto.server.objects.feature.ScreenshotCropper",
    listOf()
) {
    ScreenshotCropper()
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.feature.ScreenshotTaker",
    listOf()
) {
    ScreenshotTaker()
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.AccumulateSink",
    listOf("input")
) { args ->
    AccumulateSink(args[0] as RequiredInput<Any>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.AppendText",
    listOf("prefix", "suffix", "output")
) { args ->
    AppendText(args[0] as OptionalInput<Any>, args[1] as OptionalInput<Any>, args[2] as RequiredOutput<String>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.CountSink",
    listOf("input")
) { args ->
    CountSink(args[0] as RequiredInput<*>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.DivisibleFilter",
    listOf("input", "output", "divisor")
) { args ->
    DivisibleFilter(args[0] as RequiredInput<Int>, args[1] as OptionalOutput<Int>, args[2] as Int)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.IntRangeSource",
    listOf("output", "from", "to")
) { args ->
    IntRangeSource(args[0] as StreamOutput<Int>, args[1] as Int, args[2] as Int)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.PrimeFilter",
    listOf("input", "output")
) { args ->
    PrimeFilter(args[0] as RequiredInput<Int>, args[1] as OptionalOutput<Int>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.RepeatProcessor",
    listOf("input", "output", "times")
) { args ->
    RepeatProcessor(args[0] as RequiredInput<Any>, args[1] as BatchOutput<Any>, args[2] as Int)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.ReplaceProcessor",
    listOf("input", "output", "replacement")
) { args ->
    ReplaceProcessor(args[0] as RequiredInput<*>, args[1] as OptionalOutput<String>, args[2] as String)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.graph.SelectLast",
    listOf("first", "second", "output")
) { args ->
    SelectLast(args[0] as OptionalInput<Any>, args[1] as OptionalInput<Any>, args[2] as RequiredOutput<Any>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.logic.LogicTraceStore",
    listOf()
) {
    LogicTraceStore
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.plugin.PluginDocument",
    listOf("jarPath", "selfLocation")
) { args ->
    PluginDocument(args[0] as String, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.report.ReportDocument",
    listOf("input", "formula", "previewAll", "filter", "previewFiltered", "analysis", "output", "selfLocation")
) { args ->
    ReportDocument(args[0] as InputSpec, args[1] as FormulaSpec, args[2] as PreviewSpec, args[3] as FilterSpec, args[4] as PreviewSpec, args[5] as AnalysisSpec, args[6] as OutputSpec, args[7] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.CloseBrowser",
    listOf()
) {
    CloseBrowser()
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.FocusElement",
    listOf("target")
) { args ->
    FocusElement(args[0] as TargetSpec)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.GoTo",
    listOf("location")
) { args ->
    GoTo(args[0] as String)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.OpenBrowser",
    listOf("extensionFiles")
) { args ->
    OpenBrowser(args[0] as List<String>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.SendEscape",
    listOf()
) {
    SendEscape()
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.VisualClick",
    listOf("target")
) { args ->
    VisualClick(args[0] as TargetSpec)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.VisualFormSubmit",
    listOf("target")
) { args ->
    VisualFormSubmit(args[0] as TargetSpec)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.browser.VisualSendKeys",
    listOf("text", "target")
) { args ->
    VisualSendKeys(args[0] as String, args[1] as TargetSpec)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.DisplayValue",
    listOf("text")
) { args ->
    DisplayValue(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.logic.BooleanLiteral",
    listOf("value")
) { args ->
    BooleanLiteral(args[0] as Boolean)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.logic.DivisibleCheck",
    listOf("number", "divisor")
) { args ->
    DivisibleCheck(args[0] as ObjectLocation, args[1] as Double)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.logic.LogicalAnd",
    listOf("condition", "and")
) { args ->
    LogicalAnd(args[0] as ObjectLocation, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.logic.LogicalNot",
    listOf("negate")
) { args ->
    LogicalNot(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.NumberLiteral",
    listOf("value")
) { args ->
    NumberLiteral(args[0] as Double)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.NumberRange",
    listOf("from", "to")
) { args ->
    NumberRange(args[0] as Int, args[1] as Int)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.script.TextLiteral",
    listOf("value")
) { args ->
    TextLiteral(args[0] as String)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.SequenceDocument",
    listOf("steps", "selfLocation")
) { args ->
    SequenceDocument(args[0] as List<ObjectLocation>, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.BooleanLiteralStep",
    listOf("value", "selfLocation")
) { args ->
    BooleanLiteralStep(args[0] as Boolean, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.DisplayValueStep",
    listOf("text", "selfLocation")
) { args ->
    DisplayValueStep(args[0] as ObjectLocation, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.InvokeSequenceStep",
    listOf("sequence")
) { args ->
    InvokeSequenceStep(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.MultiSequenceStep",
    listOf("steps")
) { args ->
    MultiSequenceStep(args[0] as List<ObjectLocation>)
}
    }
}
