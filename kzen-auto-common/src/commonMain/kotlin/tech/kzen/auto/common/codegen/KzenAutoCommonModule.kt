// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2023-07-20T09:49:39.610334900
package tech.kzen.auto.common.codegen

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.auto.common.objects.document.feature.TargetSpecCreator
import tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner
import tech.kzen.auto.common.objects.document.graph.DataflowWiring
import tech.kzen.auto.common.objects.document.graph.EdgesDefiner
import tech.kzen.auto.common.objects.document.graph.GraphDocument
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.script.action.PrintlnAction
import tech.kzen.auto.common.objects.document.script.action.ReferenceAction
import tech.kzen.auto.common.objects.document.script.action.SleepAction
import tech.kzen.auto.common.objects.document.script.control.ConditionalExpression
import tech.kzen.auto.common.objects.document.script.control.ListItem
import tech.kzen.auto.common.objects.document.script.control.ListMapping
import tech.kzen.auto.common.objects.document.script.invoke.InvokeCall
import tech.kzen.auto.common.objects.document.script.invoke.InvokeInput
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep


@Suppress("UNCHECKED_CAST")
object KzenAutoCommonModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.feature.FeatureDocument",
    listOf("objectLocation", "documentNotation")
) { args ->
    FeatureDocument(args[0] as ObjectLocation, args[1] as DocumentNotation)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.feature.TargetSpecCreator",
    listOf()
) {
    TargetSpecCreator()
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner",
    listOf()
) {
    TargetSpecDefiner()
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.graph.DataflowWiring",
    listOf()
) {
    DataflowWiring()
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.graph.EdgesDefiner",
    listOf()
) {
    EdgesDefiner()
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.graph.GraphDocument",
    listOf("vertices", "edges")
) { args ->
    GraphDocument(args[0] as List<Dataflow<*>>, args[1] as List<EdgeDescriptor>)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec\$Definer",
    listOf()
) {
    AnalysisSpec.Definer
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec\$Definer",
    listOf()
) {
    FilterSpec.Definer
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.report.spec.FormulaSpec\$Definer",
    listOf()
) {
    FormulaSpec.Definer
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.report.spec.input.InputSpec\$Definer",
    listOf()
) {
    InputSpec.Definer
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec\$Definer",
    listOf()
) {
    OutputSpec.Definer
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.report.spec.PreviewSpec\$Definer",
    listOf()
) {
    PreviewSpec.Definer
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.action.PrintlnAction",
    listOf("message")
) { args ->
    PrintlnAction(args[0] as String)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.action.ReferenceAction",
    listOf("value")
) { args ->
    ReferenceAction(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.action.SleepAction",
    listOf("seconds")
) { args ->
    SleepAction(args[0] as Double)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.control.ConditionalExpression",
    listOf("condition", "then", "else")
) { args ->
    ConditionalExpression(args[0] as ObjectLocation, args[1] as List<ObjectLocation>, args[2] as List<ObjectLocation>)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.control.ListItem",
    listOf("selfLocation")
) { args ->
    ListItem(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.control.ListMapping",
    listOf("selfLocation", "items", "steps")
) { args ->
    ListMapping(args[0] as ObjectLocation, args[1] as ObjectLocation, args[2] as List<ObjectLocation>)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.invoke.InvokeCall",
    listOf("selfLocation", "script")
) { args ->
    InvokeCall(args[0] as ObjectLocation, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.invoke.InvokeInput",
    listOf()
) {
    InvokeInput()
}

reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.script.ScriptDocument",
    listOf("steps")
) { args ->
    ScriptDocument(args[0] as List<ScriptStep>)
}
    }
}
