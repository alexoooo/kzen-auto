package tech.kzen.auto.client.objects.document.script.step.control.foreach

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.display.branch.*
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.step.control.BranchStepDisplayProps
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ForEachStepDisplayState: ScriptStepDisplayBaseState {
    var itemTypeMetadata: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ForEachStepDisplay(
    props: BranchStepDisplayProps
):
    ScriptStepDisplayBase<BranchStepDisplayProps, ForEachStepDisplayState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val scriptCommander: ScriptCommander,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            ForEachStepDisplay::class.react {
                attributeEditorManager = this@Wrapper.attributeEditorManager
                stepDisplayManager = this@Wrapper.stepDisplayManager.wrapper!!
                scriptCommander = this@Wrapper.scriptCommander
                clientStateGlobal = this@Wrapper.clientStateGlobal
                objectStableMapper = this@Wrapper.objectStableMapper
                mirroredGraphStore = this@Wrapper.mirroredGraphStore

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The loop item binding (ForEachItemBinding) lives in this ForEach's `item` branch; its type is the
    // items-collection element type. Surfaced beside the "Item" branch label (see renderSteps).
    override fun onScriptStateExtra(scriptState: ScriptState) {
        val itemObjectPath = scriptState
            .documentNotation
            .directNestedObjectPaths(
                props.common.objectLocation.objectPath, ScriptConventions.itemAttributeName)
            .firstOrNull()

        val itemTypeMetadata = itemObjectPath?.let {
            scriptState
                .validationState
                .scriptValidation
                ?.stepValidations
                ?.get(it)
                ?.typeMetadata
                ?.toSimple()
        }

        if (state.itemTypeMetadata == itemTypeMetadata) {
            return
        }

        setState {
            this.itemTypeMetadata = itemTypeMetadata
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        branchHeaderSlab(
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = state.stepTrace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore,
            typeMetadata = state.stepValidation?.typeMetadata?.toSimple(),
            validationError = state.stepValidation?.errorMessage
        ) {
            props.attributeEditorManager.child(this) {
                this.objectLocation = props.common.objectLocation
                this.attributeName = ScriptConventions.itemsAttributeName
            }

            renderCurrentItem()
        }

        // Recessed-stage chrome wrapper, mirroring the page-level header/sidebar casting a shadow
        // onto the gray stage (same treatment as IfStepDisplay's branches). The white items slab
        // above plays the "header" role, the white trunk the "sidebar". A single branch ("Each"),
        // so just the shared seam + top down-shadow, plus the vertical ledge down the trunk's edge.
        div {
            css {
                position = Position.relative
            }

            branchStageLedge()

            // Outer frame: hairline + soft shade down the trunk's left edge and across its bottom,
            // with rounded bottom corners — completing the white "⌐" card frame (header = top).
            branchStageBase()

            branchStageSeam()
            branchStageTopShadow {
                renderSteps()
            }
        }
    }


    // The current iteration's item value, surfaced live as the ForEach runs (traced as the step's
    // detail by ForEachStep). Hidden until there's an item to show.
    private fun ChildrenBuilder.renderCurrentItem() {
        val detail = state.stepTrace?.detail
        if (detail == null || detail is NullExecutionValue) {
            return
        }

        div {
            css {
                marginTop = 0.25.em
                marginBottom = 0.5.em
                fontSize = 0.85.em
                color = Color("gray")
            }

            +"item: "
            span {
                css {
                    fontWeight = FontWeight.bold
                    color = NamedColor.black
                }
                +executionValueText(detail)
            }
        }
    }


    private fun executionValueText(value: ExecutionValue): String {
        return when (value) {
            is ScalarExecutionValue -> value.get().toString()
            is ListExecutionValue -> value.values.map { it.get() }.toString()
            else -> value.toString()
        }
    }


    private fun ChildrenBuilder.renderSteps() {
        scriptBranchContainer(
            label = "Item",
            branchLocation = AttributeLocation(props.common.objectLocation, ScriptConventions.stepsAttributePath),
            stepDisplayManager = props.stepDisplayManager,
            scriptCommander = props.scriptCommander,
            roundedBottom = true,
            clientStateGlobal = props.clientStateGlobal,
            mirroredGraphStore = props.mirroredGraphStore,
            objectStableMapper = props.objectStableMapper,
            labelType = state.itemTypeMetadata)
    }
}
