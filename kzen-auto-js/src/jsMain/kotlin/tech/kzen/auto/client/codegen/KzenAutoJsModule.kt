// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2023-12-15T00:01:40.117766700
package tech.kzen.auto.client.codegen

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.DefaultAttributeEditor
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.auto.client.objects.document.data.DataFormatController
import tech.kzen.auto.client.objects.document.feature.FeatureController
import tech.kzen.auto.client.objects.document.graph.edit.AttributeEditorManagerOld
import tech.kzen.auto.client.objects.document.graph.edit.AttributeEditorOld
import tech.kzen.auto.client.objects.document.graph.edit.DefaultAttributeEditorOld
import tech.kzen.auto.client.objects.document.graph.GraphController
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.objects.document.plugin.PluginController
import tech.kzen.auto.client.objects.document.registry.ObjectRegistryController
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander
import tech.kzen.auto.client.objects.document.sequence.command.SequenceStepCommander
import tech.kzen.auto.client.objects.document.sequence.display.edit.SelectLogicEditor
import tech.kzen.auto.client.objects.document.sequence.display.edit.SelectStepEditor
import tech.kzen.auto.client.objects.document.sequence.display.edit.TargetSpecEditor
import tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayDefault
import tech.kzen.auto.client.objects.document.sequence.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayWrapper
import tech.kzen.auto.client.objects.document.sequence.SequenceController
import tech.kzen.auto.client.objects.document.sequence.step.control.IfStepDisplay
import tech.kzen.auto.client.objects.document.sequence.step.control.mapping.MappingStepCommander
import tech.kzen.auto.client.objects.document.sequence.step.control.mapping.MappingStepDisplay
import tech.kzen.auto.client.objects.document.sequence.step.control.MultiStepDisplay
import tech.kzen.auto.client.objects.document.sequence.step.control.RunStepArgumentsEditor
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.ProjectController
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.objects.ribbon.HeaderController
import tech.kzen.auto.client.objects.ribbon.RibbonGroup
import tech.kzen.auto.client.objects.ribbon.RibbonTool


@Suppress("UNCHECKED_CAST", "KotlinRedundantDiagnosticSuppress")
object KzenAutoJsModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager\$Wrapper",
    listOf("attributeEditors")
) { args ->
    AttributeEditorManager.Wrapper(args[0] as List<AttributeEditor>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.common.attribute.DefaultAttributeEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    DefaultAttributeEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.data.DataFormatController\$Wrapper",
    listOf("archetype")
) { args ->
    DataFormatController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.feature.FeatureController\$Wrapper",
    listOf("archetype")
) { args ->
    FeatureController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.graph.edit.AttributeEditorManagerOld\$Wrapper",
    listOf("attributeEditors")
) { args ->
    AttributeEditorManagerOld.Wrapper(args[0] as List<AttributeEditorOld>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.graph.edit.DefaultAttributeEditorOld\$Wrapper",
    listOf("objectLocation")
) { args ->
    DefaultAttributeEditorOld.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.graph.GraphController\$Wrapper",
    listOf("archetype", "attributeController", "ribbonController")
) { args ->
    GraphController.Wrapper(args[0] as ObjectLocation, args[1] as AttributeEditorManagerOld.Wrapper, args[2] as RibbonController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.plugin.PluginController\$Wrapper",
    listOf("archetype")
) { args ->
    PluginController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.registry.ObjectRegistryController\$Wrapper",
    listOf("archetype")
) { args ->
    ObjectRegistryController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.report.ReportController\$Wrapper",
    listOf("archetype")
) { args ->
    ReportController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander",
    listOf("stepCommanders")
) { args ->
    SequenceCommander(args[0] as List<SequenceStepCommander>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.display.edit.SelectLogicEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    SelectLogicEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.display.edit.SelectStepEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    SelectStepEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.display.edit.TargetSpecEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    TargetSpecEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayDefault\$Wrapper",
    listOf("objectLocation", "attributeEditorManager")
) { args ->
    SequenceStepDisplayDefault.Wrapper(args[0] as ObjectLocation, args[1] as AttributeEditorManager.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.display.StepDisplayManager\$Wrapper",
    listOf("stepDisplays", "handle")
) { args ->
    StepDisplayManager.Wrapper(args[0] as List<SequenceStepDisplayWrapper>, args[1] as StepDisplayManager.Handle)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.display.StepDisplayManager\$Handle",
    listOf()
) {
    StepDisplayManager.Handle()
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.SequenceController\$Wrapper",
    listOf("archetype", "stepDisplayManager", "sequenceCommander", "ribbonController")
) { args ->
    SequenceController.Wrapper(args[0] as ObjectLocation, args[1] as StepDisplayManager.Wrapper, args[2] as SequenceCommander, args[3] as RibbonController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.control.IfStepDisplay\$Wrapper",
    listOf("objectLocation", "attributeEditorManager", "stepDisplayManager", "sequenceCommander")
) { args ->
    IfStepDisplay.Wrapper(args[0] as ObjectLocation, args[1] as AttributeEditorManager.Wrapper, args[2] as StepDisplayManager.Handle, args[3] as SequenceCommander)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.control.mapping.MappingStepCommander",
    listOf("mappingStepArchetype", "itemArchetype")
) { args ->
    MappingStepCommander(args[0] as ObjectLocation, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.control.mapping.MappingStepDisplay\$Wrapper",
    listOf("objectLocation", "attributeEditorManager", "stepDisplayManager", "sequenceCommander")
) { args ->
    MappingStepDisplay.Wrapper(args[0] as ObjectLocation, args[1] as AttributeEditorManager.Wrapper, args[2] as StepDisplayManager.Handle, args[3] as SequenceCommander)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.control.MultiStepDisplay\$Wrapper",
    listOf("objectLocation", "stepDisplayManager", "sequenceCommander")
) { args ->
    MultiStepDisplay.Wrapper(args[0] as ObjectLocation, args[1] as StepDisplayManager.Handle, args[2] as SequenceCommander)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.control.RunStepArgumentsEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    RunStepArgumentsEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.StageController\$Wrapper",
    listOf("documentControllers")
) { args ->
    StageController.Wrapper(args[0] as List<DocumentController>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.ProjectController\$Wrapper",
    listOf("sidebarController", "headerController", "stageController")
) { args ->
    ProjectController.Wrapper(args[0] as SidebarController.Wrapper, args[1] as HeaderController.Wrapper, args[2] as StageController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.ribbon.HeaderController\$Wrapper",
    listOf("documentControllers")
) { args ->
    HeaderController.Wrapper(args[0] as List<DocumentController>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.ribbon.RibbonController\$Wrapper",
    listOf("actionTypes", "ribbonGroups")
) { args ->
    RibbonController.Wrapper(args[0] as List<ObjectLocation>, args[1] as List<RibbonGroup>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.ribbon.RibbonGroup",
    listOf("title", "archetype", "children")
) { args ->
    RibbonGroup(args[0] as String, args[1] as ObjectLocation, args[2] as List<RibbonTool>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.ribbon.RibbonTool",
    listOf("delegate")
) { args ->
    RibbonTool(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.sidebar.SidebarController\$Wrapper",
    listOf("archetypes")
) { args ->
    SidebarController.Wrapper(args[0] as List<ObjectLocation>)
}
    }
}
