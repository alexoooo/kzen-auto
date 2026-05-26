package tech.kzen.auto.client.objects.document.custom.view.obj

import emotion.react.css
import mui.material.CardContent
import mui.material.Paper
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.dragdrop.DropMarker
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.common.dragdrop.dropIndicator
import tech.kzen.auto.client.objects.document.custom.CustomTheme
import tech.kzen.auto.client.objects.document.custom.view.CustomViewStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectProps: Props {
    var objectLocation: ObjectLocation
    var info: CustomObjectInfo
    var viewStore: CustomViewStore
    var attributeEditorManager: AttributeEditorManager.Wrapper

    var indexInDocument: Int
    var dropMarker: DropMarker?
}


external interface CustomObjectState: State {
    var isHovering: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomObject(
    props: CustomObjectProps
):
    RPureComponent<CustomObjectProps, CustomObjectState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private var detachedRunner: CustomObjectDetachedRunner? = null
    private var taskRunner: CustomObjectTaskRunner? = null


    private fun detachedRunnerOrCreate(): CustomObjectDetachedRunner {
        val existing = detachedRunner
        if (existing != null) {
            return existing
        }
        val created = CustomObjectDetachedRunner()
        detachedRunner = created
        return created
    }


    private fun taskRunnerOrCreate(): CustomObjectTaskRunner {
        val existing = taskRunner
        if (existing != null) {
            return existing
        }
        val created = CustomObjectTaskRunner()
        taskRunner = created
        return created
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomObjectState.init(props: CustomObjectProps) {
        isHovering = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseEnter() {
        if (!state.isHovering) {
            setState { isHovering = true }
        }
    }


    private fun onMouseLeave() {
        if (state.isHovering) {
            setState { isHovering = false }
        }
    }


    private fun onDragOver(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()
        val rect = event.currentTarget.getBoundingClientRect()
        val dropAfter = event.clientY > rect.top + rect.height / 2
        props.viewStore.onDragOver(props.indexInDocument, dropAfter)
    }


    private fun onDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()
        props.viewStore.onDrop()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                marginBottom = 1.em
            }

            onMouseEnter = { onMouseEnter() }
            onMouseLeave = { onMouseLeave() }
            onDragOver = ::onDragOver
            onDrop = ::onDrop

            dragHandle(
                isVisible = state.isHovering || props.dropMarker != null,
                handleColor = CustomTheme.mutedText,
                onStart = { props.viewStore.onDragStart(props.indexInDocument) },
                onEnd = { props.viewStore.onDragEnd() })
            dropIndicator(props.dropMarker)

            Paper {
                sx {
                    if (props.info.isAbstract) {
                        backgroundColor = CustomTheme.abstractBackground
                        borderStyle = LineStyle.dashed
                        borderWidth = 1.px
                        borderColor = CustomTheme.abstractBorder
                        color = CustomTheme.mutedText
                    }
                    else {
                        backgroundColor = NamedColor.white
                    }

                    if (props.info.isExported) {
                        filter = dropShadow(0.px, 0.px, 4.px, Color("rgba(255, 193, 7, 0.55)"))
                    }
                }

                CardContent {
                    val detached =
                        if (props.info.isDetached && !props.info.isAbstract) {
                            detachedRunnerOrCreate()
                        }
                        else {
                            null
                        }

                    val task =
                        if (props.info.isTask && !props.info.isAbstract) {
                            taskRunnerOrCreate()
                        }
                        else {
                            null
                        }

                    CustomObjectHeader::class.react {
                        objectLocation = props.objectLocation
                        info = props.info
                        viewStore = props.viewStore
                        if (detached != null || task != null) {
                            headerExtra = { headerBuilder ->
                                with(headerBuilder) {
                                    if (detached != null) {
                                        CustomObjectDetachedHeader::class.react {
                                            this.runner = detached
                                            this.objectLocation = props.objectLocation
                                        }
                                    }
                                    if (task != null) {
                                        CustomObjectTaskHeader::class.react {
                                            this.runner = task
                                            this.objectLocation = props.objectLocation
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (detached != null) {
                        CustomObjectDetachedBody::class.react {
                            this.runner = detached
                        }
                    }

                    if (task != null) {
                        CustomObjectTaskBody::class.react {
                            this.runner = task
                        }
                    }

                    val objectMetadata = props.info.objectMetadata
                    if (objectMetadata == null) {
                        div {
                            css {
                                fontStyle = FontStyle.italic
                                color = CustomTheme.warningText
                            }
                            +"(metadata unavailable)"
                        }
                    }
                    else {
                        renderAttributes(objectMetadata)
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderAttributes(objectMetadata: ObjectMetadata) {
        for (entry in objectMetadata.attributes.map) {
            val attributeName = entry.key
            if (CustomConventions.isManaged(attributeName)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.5.em
                }

                props.attributeEditorManager.child(this) {
                    this.objectLocation = props.objectLocation
                    this.attributeName = attributeName
                }
            }
        }
    }
}
