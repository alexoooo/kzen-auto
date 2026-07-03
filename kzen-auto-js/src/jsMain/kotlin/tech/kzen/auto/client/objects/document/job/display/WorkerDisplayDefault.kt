package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.JobChannelPorts
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface WorkerDisplayDefaultProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore

    // Optional extra content rendered at the bottom of the card body — null for a plain Worker; a per-type display
    // (PreviewWorkerDisplay / ExploreWorkerDisplay) uses it to add its sample table / download button. NB: plain
    // (non-receiver) function type — receiver function types are prohibited in external declarations (mirrors
    // ScriptStepDisplayDefault.expandedBodyExtra); the callee invokes it with the body's ChildrenBuilder.
    var bodyExtra: ((ChildrenBuilder) -> Unit)?
}


external interface WorkerDisplayDefaultState: State {
    var objectMetadata: ObjectMetadata?
}


//---------------------------------------------------------------------------------------------------------------------
// The default Worker card: a white node card with the Worker name, live status / counts, generic per-attribute
// summary views (the Run drill-in link renders here via a `summary:` marker — no Worker-type branch), and generic
// per-attribute editors. Every Worker inherits this via `display: WorkerDisplayDefault` on the base Worker; a
// per-type display (Preview / Explore / Summary) wraps it and adds its behaviour through bodyExtra — the exact
// ScriptStepDisplayDefault / RunStepDisplay composition. Observes ClientStateGlobal for its own metadata, so the
// generic slot threads only the minimal WorkerDisplayPropsCommon (see CC-17).
@Suppress("unused")
class WorkerDisplayDefault(
    props: WorkerDisplayDefaultProps
):
    RPureComponent<WorkerDisplayDefaultProps, WorkerDisplayDefaultState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val workerBorder = Color("#c4c4c4")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        WorkerDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            WorkerDisplayDefault::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val objectMetadata = clientState
            .graphStructure()
            .graphMetadata
            .objectMetadata[props.common.objectLocation]
            ?: return

        // Value compare (== over the metadata data class): skip setState on no-op clientState publishes so the
        // RPureComponent isn't defeated by an unchanged-metadata broadcast (mirrors ScriptStepDisplayDefault).
        if (state.objectMetadata == objectMetadata) {
            return
        }

        setState {
            this.objectMetadata = objectMetadata
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onDelete() {
        async {
            props.mirroredGraphStore.apply(RemoveObjectCommand(props.common.objectLocation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val objectMetadata = state.objectMetadata
            ?: return

        div {
            css {
                padding = Padding(0.5.em, 0.75.em, 0.5.em, 0.75.em)
                border = Border(1.px, LineStyle.solid, workerBorder)
                borderRadius = 3.px
                backgroundColor = NamedColor.white
            }

            cardHeader {
                span {
                    css {
                        fontFamily = FontFamily.monospace
                        marginLeft = 0.5.em
                        color = NamedColor.gray
                    }
                    +statusText(props.common.progress)
                }

                renderAttributeSummaries(objectMetadata)
            }

            renderAttributeEditors(objectMetadata)

            props.bodyExtra?.invoke(this)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.cardHeader(
        trailing: ChildrenBuilder.() -> Unit
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginBottom = 0.25.em
            }

            span {
                css {
                    fontWeight = FontWeight.bold
                }
                +props.common.objectLocation.objectPath.name.value
            }

            trailing()

            div {
                css {
                    flexGrow = number(1.0)
                }
            }

            IconButton {
                title = "Delete"
                size = Size.small
                onClick = { onDelete() }
                icon("material-symbols:delete-outline") {}
            }
        }
    }


    private fun statusText(progress: JobWorkerProgress?): String {
        if (progress == null) {
            return "—"
        }

        val parts = mutableListOf<String>()
        progress.status?.let { parts.add(it) }
        if (progress.counts.isNotEmpty()) {
            parts.add(progress.counts.entries.joinToString(" ") { "${it.key}=${it.value}" })
        }
        return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    }


    // Generic per-attribute summary views: any attribute whose metadata declares a `summary:` view is rendered
    // through the shared AttributeViewManager — no Worker-type gate (see CC-17). A RunWorker's `instructions`
    // declares ReferenceLinkAttributeView, so its child-document drill-in link renders here; any future Worker
    // attribute that declares a summary view gets it for free. Mirrors the Script step header's summary row.
    private fun ChildrenBuilder.renderAttributeSummaries(objectMetadata: ObjectMetadata) {
        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            val hasSummaryView = attributeMetadata
                .attributeMetadataNotation
                .get(AttributeViewManager.summaryAttributePath.toNesting())
                ?.asString()
                ?.isNotEmpty()
                ?: false
            if (! hasSummaryView) {
                continue
            }

            div {
                key = Key(attributeName.value)
                css {
                    marginLeft = 0.75.em
                }
                props.attributeViewManager.child(this) {
                    this.objectLocation = props.common.objectLocation
                    this.attributeName = attributeName
                }
            }
        }
    }


    // An editor for each non-managed attribute via the shared AttributeEditorManager — scalars (path, delimiter,
    // ...) fall to the default value editor; channel-reference attributes dispatch to SelectChannelEditor via their
    // `editor:` metadata. Channel-endpoint ports are order-managed (the gold pipes between cards), not per-Worker.
    private fun ChildrenBuilder.renderAttributeEditors(objectMetadata: ObjectMetadata) {
        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            // Per-output channel config lives in a free-form `channels` map, which infers to no metadata and so
            // never appears in this meta-attribute loop — no explicit exclusion needed. Channel-endpoint ports
            // are order-managed (the gold pipes between cards), not per-Worker editors.
            if (AutoConventions.isManaged(attributeName) ||
                    JobChannelPorts.isChannelPort(attributeMetadata.type)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.25.em
                }
                renderAttributeEditor(attributeName)
            }
        }
    }


    private fun ChildrenBuilder.renderAttributeEditor(attributeName: AttributeName) {
        props.attributeEditorManager.child(this) {
            this.objectLocation = props.common.objectLocation
            this.attributeName = attributeName
        }
    }
}
