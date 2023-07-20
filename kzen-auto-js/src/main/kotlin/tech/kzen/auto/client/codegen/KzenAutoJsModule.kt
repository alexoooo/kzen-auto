// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2023-07-20T09:49:39.693841900
package tech.kzen.auto.client.codegen

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.auto.client.objects.document.feature.FeatureController
import tech.kzen.auto.client.objects.document.graph.GraphController
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.objects.document.plugin.PluginController
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.command.StepCommander
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.attribute.SelectFeatureEditor
import tech.kzen.auto.client.objects.document.script.step.attribute.SelectScriptEditor
import tech.kzen.auto.client.objects.document.script.step.attribute.SelectStepEditor
import tech.kzen.auto.client.objects.document.script.step.attribute.TargetSpecEditor
import tech.kzen.auto.client.objects.document.script.step.display.control.ConditionalStepDisplay
import tech.kzen.auto.client.objects.document.script.step.display.control.MappingCommander
import tech.kzen.auto.client.objects.document.script.step.display.control.MappingStepDisplay
import tech.kzen.auto.client.objects.document.script.step.display.DefaultStepDisplay
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.client.objects.document.sequence.command.IfStepCommander
import tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander
import tech.kzen.auto.client.objects.document.sequence.command.SequenceStepCommander
import tech.kzen.auto.client.objects.document.sequence.SequenceController
import tech.kzen.auto.client.objects.document.sequence.step.SequenceStepController
import tech.kzen.auto.client.objects.document.sequence.step.attribute.SelectSequenceStepEditor
import tech.kzen.auto.client.objects.document.sequence.step.display.condition.IfStepDisplay
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayDefault
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayWrapper
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.ProjectController
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.objects.ribbon.HeaderController
import tech.kzen.auto.client.objects.ribbon.RibbonGroup
import tech.kzen.auto.client.objects.ribbon.RibbonTool


@Suppress("UNCHECKED_CAST")
object KzenAutoJsModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.common.AttributeController\$Wrapper",
    listOf("attributeEditors")
) { args ->
    AttributeController.Wrapper(args[0] as List<AttributeEditorWrapper>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    DefaultAttributeEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.feature.FeatureController\$Wrapper",
    listOf("archetype")
) { args ->
    FeatureController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.graph.GraphController\$Wrapper",
    listOf("archetype", "attributeController", "ribbonController")
) { args ->
    GraphController.Wrapper(args[0] as ObjectLocation, args[1] as AttributeController.Wrapper, args[2] as RibbonController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.plugin.PluginController\$Wrapper",
    listOf("archetype")
) { args ->
    PluginController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.report.ReportController\$Wrapper",
    listOf("archetype")
) { args ->
    ReportController.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.command.ScriptCommander",
    listOf("stepCommanders")
) { args ->
    ScriptCommander(args[0] as List<StepCommander>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.ScriptController\$Wrapper",
    listOf("archetype", "stepController", "scriptCommander", "ribbonController")
) { args ->
    ScriptController.Wrapper(args[0] as ObjectLocation, args[1] as StepController.Wrapper, args[2] as ScriptCommander, args[3] as RibbonController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.attribute.SelectFeatureEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    SelectFeatureEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.attribute.SelectScriptEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    SelectScriptEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.attribute.SelectStepEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    SelectStepEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.attribute.TargetSpecEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    TargetSpecEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.display.control.ConditionalStepDisplay\$Wrapper",
    listOf("objectLocation", "attributeController", "scriptCommander", "stepControllerHandle")
) { args ->
    ConditionalStepDisplay.Wrapper(args[0] as ObjectLocation, args[1] as AttributeController.Wrapper, args[2] as ScriptCommander, args[3] as StepController.Handle)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.display.control.MappingCommander",
    listOf("stepArchetype", "itemArchetype")
) { args ->
    MappingCommander(args[0] as ObjectLocation, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.display.control.MappingStepDisplay\$Wrapper",
    listOf("objectLocation", "attributeController", "scriptCommander", "stepControllerHandle")
) { args ->
    MappingStepDisplay.Wrapper(args[0] as ObjectLocation, args[1] as AttributeController.Wrapper, args[2] as ScriptCommander, args[3] as StepController.Handle)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.display.DefaultStepDisplay\$Wrapper",
    listOf("objectLocation", "attributeController")
) { args ->
    DefaultStepDisplay.Wrapper(args[0] as ObjectLocation, args[1] as AttributeController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.StepController\$Wrapper",
    listOf("stepDisplays", "handle")
) { args ->
    StepController.Wrapper(args[0] as List<StepDisplayWrapper>, args[1] as StepController.Handle)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.script.step.StepController\$Handle",
    listOf()
) {
    StepController.Handle()
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.command.IfStepCommander",
    listOf("stepArchetype", "branchArchetype")
) { args ->
    IfStepCommander(args[0] as ObjectLocation, args[1] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander",
    listOf("stepCommanders")
) { args ->
    SequenceCommander(args[0] as List<SequenceStepCommander>)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.SequenceController\$Wrapper",
    listOf("archetype", "stepController", "sequenceCommander", "ribbonController")
) { args ->
    SequenceController.Wrapper(args[0] as ObjectLocation, args[1] as SequenceStepController.Wrapper, args[2] as SequenceCommander, args[3] as RibbonController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.attribute.SelectSequenceStepEditor\$Wrapper",
    listOf("objectLocation")
) { args ->
    SelectSequenceStepEditor.Wrapper(args[0] as ObjectLocation)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.display.condition.IfStepDisplay\$Wrapper",
    listOf("objectLocation", "attributeController")
) { args ->
    IfStepDisplay.Wrapper(args[0] as ObjectLocation, args[1] as AttributeController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayDefault\$Wrapper",
    listOf("objectLocation", "attributeController")
) { args ->
    SequenceStepDisplayDefault.Wrapper(args[0] as ObjectLocation, args[1] as AttributeController.Wrapper)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.SequenceStepController\$Wrapper",
    listOf("stepDisplays", "handle")
) { args ->
    SequenceStepController.Wrapper(args[0] as List<SequenceStepDisplayWrapper>, args[1] as SequenceStepController.Handle)
}

reflectionRegistry.put(
    "tech.kzen.auto.client.objects.document.sequence.step.SequenceStepController\$Handle",
    listOf()
) {
    SequenceStepController.Handle()
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
