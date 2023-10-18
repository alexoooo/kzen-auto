// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2023-10-18T11:53:08.990368500
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
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.auto.server.objects.report.ReportDocument
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.server.objects.sequence.SequenceDocument
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserClickStep
import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserCloseStep
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserEscapeStep
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserFocusStep
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserGetStep
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserOpenStep
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserSubmitStep
import tech.kzen.auto.server.objects.sequence.step.browser.BrowserWriteStep
import tech.kzen.auto.server.objects.sequence.step.control.IfStep
import tech.kzen.auto.server.objects.sequence.step.control.mapping.MappingItemStep
import tech.kzen.auto.server.objects.sequence.step.control.mapping.MappingStep
import tech.kzen.auto.server.objects.sequence.step.control.MultiStep
import tech.kzen.auto.server.objects.sequence.step.control.RunStep
import tech.kzen.auto.server.objects.sequence.step.control.WaitStep
import tech.kzen.auto.server.objects.sequence.step.logic.DivisibleCheckStep
import tech.kzen.auto.server.objects.sequence.step.logic.LogicalAndStep
import tech.kzen.auto.server.objects.sequence.step.value.BooleanLiteralStep
import tech.kzen.auto.server.objects.sequence.step.value.DisplayValueStep
import tech.kzen.auto.server.objects.sequence.step.value.NumberLiteralStep
import tech.kzen.auto.server.objects.sequence.step.value.NumberRangeStep
import tech.kzen.auto.server.objects.sequence.step.value.TextLiteralStep


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
    "tech.kzen.auto.server.objects.sequence.SequenceDocument",
    listOf("steps", "selfLocation")
) { args ->
    SequenceDocument(args[0] as List<ObjectLocation>, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserClickStep",
    listOf("target", "selfLocation")
) { args ->
    BrowserClickStep(args[0] as TargetSpec, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserCloseStep",
    listOf("selfLocation")
) { args ->
    BrowserCloseStep(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserEscapeStep",
    listOf("selfLocation")
) { args ->
    BrowserEscapeStep(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserFocusStep",
    listOf("target", "selfLocation")
) { args ->
    BrowserFocusStep(args[0] as TargetSpec, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserGetStep",
    listOf("location", "selfLocation")
) { args ->
    BrowserGetStep(args[0] as String, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserOpenStep",
    listOf("selfLocation")
) { args ->
    BrowserOpenStep(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserSubmitStep",
    listOf("target", "selfLocation")
) { args ->
    BrowserSubmitStep(args[0] as TargetSpec, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.browser.BrowserWriteStep",
    listOf("text", "target", "selfLocation")
) { args ->
    BrowserWriteStep(args[0] as String, args[1] as TargetSpec, args[2] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.control.IfStep",
    listOf("condition", "then", "else")
) { args ->
    IfStep(args[0] as ObjectLocation, args[1] as List<ObjectLocation>, args[2] as List<ObjectLocation>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.control.mapping.MappingItemStep",
    listOf("selfLocation")
) { args ->
    MappingItemStep(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.control.mapping.MappingStep",
    listOf("items", "steps", "selfLocation")
) { args ->
    MappingStep(args[0] as ObjectLocation, args[1] as List<ObjectLocation>, args[2] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.control.MultiStep",
    listOf("steps")
) { args ->
    MultiStep(args[0] as List<ObjectLocation>)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.control.RunStep",
    listOf("instructions")
) { args ->
    RunStep(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.control.WaitStep",
    listOf("milliseconds", "selfLocation")
) { args ->
    WaitStep(args[0] as Long, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.logic.DivisibleCheckStep",
    listOf("number", "divisor", "selfLocation")
) { args ->
    DivisibleCheckStep(args[0] as ObjectLocation, args[1] as Int, args[2] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.logic.LogicalAndStep",
    listOf("condition", "and", "selfLocation")
) { args ->
    LogicalAndStep(args[0] as ObjectLocation, args[1] as ObjectLocation, args[2] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.value.BooleanLiteralStep",
    listOf("value", "selfLocation")
) { args ->
    BooleanLiteralStep(args[0] as Boolean, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.value.DisplayValueStep",
    listOf("text", "selfLocation")
) { args ->
    DisplayValueStep(args[0] as ObjectLocation, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.value.NumberLiteralStep",
    listOf("value", "selfLocation")
) { args ->
    NumberLiteralStep(args[0] as Double, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.value.NumberRangeStep",
    listOf("from", "to", "selfLocation")
) { args ->
    NumberRangeStep(args[0] as Int, args[1] as Int, args[2] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.server.objects.sequence.step.value.TextLiteralStep",
    listOf("value", "selfLocation")
) { args ->
    TextLiteralStep(args[0] as String, args[1] as ObjectLocation)
}
    }
}
