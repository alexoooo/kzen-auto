// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2023-10-18T11:53:08.812223600
package tech.kzen.auto.common.codegen

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.lib.common.model.location.ObjectLocation
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
    }
}
