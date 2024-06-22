// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2024-05-12T19:57:22.016046500
package tech.kzen.auto.common.codegen

import tech.kzen.auto.common.objects.document.data.spec.FieldFormatListSpec
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.auto.common.objects.document.feature.TargetSpecCreator
import tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner
import tech.kzen.auto.common.objects.document.graph.DataflowWiring
import tech.kzen.auto.common.objects.document.graph.EdgesDefiner
import tech.kzen.auto.common.objects.document.graph.GraphDocument
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry


@Suppress("UNCHECKED_CAST", "KotlinRedundantDiagnosticSuppress")
object KzenAutoCommonModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
reflectionRegistry.put(
    "tech.kzen.auto.common.objects.document.data.spec.FieldFormatListSpec\$Definer",
    listOf()
) {
    FieldFormatListSpec.Definer
}

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
    "tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec\$Definer",
    listOf()
) {
    ClassListSpec.Definer
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
    }
}
