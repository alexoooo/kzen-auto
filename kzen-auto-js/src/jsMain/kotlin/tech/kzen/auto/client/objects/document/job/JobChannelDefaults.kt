package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Tooltip
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface JobChannelDefaultsProps: Props {
    // `main` — the object holding the Job-wide `batchSize` / `capacity` defaults.
    var mainLocation: ObjectLocation

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
// The Job-wide channel defaults, floated at the top-right of the Job editor (mirroring Script's Parameters / Results
// controls) rather than sitting inline at the top of the stage. Two labelled numeric fields bound to `main`'s
// batchSize / capacity, applied by JobChannelSynthesis to every auto-synthesized channel that carries no per-channel
// override, plus an [i] hover-tooltip on each explaining what the knob does. Stateless: each JobChannelNumberField
// owns its own clientStateGlobal subscription, so this component just lays them out.
class JobChannelDefaults(
    props: JobChannelDefaultsProps
):
    RPureComponent<JobChannelDefaultsProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Short, direct copy (accurate to JobChannel.kt: batch = grouped transfer unit; capacity = buffered
        // batch count, 0 = unbuffered). The field's placeholder already shows the default, so it's omitted here.
        private const val batchSizeInfo =
            "How many items are grouped into each channel transfer."

        private const val capacityInfo =
            "How many batches a channel buffers before the sender waits."
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                // Floated top-right, anchored to JobController's (relative) stage — scrolls with content,
                // stacked directly beneath the Parameters control (the ResultSignatureEditor stacking
                // convention). A narrow, compact 3-row column (label, batch size, capacity).
                position = Position.absolute
                top = 2.75.em
                right = 0.5.em
                zIndex = integer(2)
                display = Display.flex
                flexDirection = FlexDirection.column
                gap = 0.35.em
                width = 11.em
            }

            // Group label, styled like Script's floating-panel labels.
            div {
                css {
                    fontSize = 0.8.em
                    color = Color("gray")
                }
                +"Channel defaults"
            }

            channelField(
                "Batch size",
                AttributePath.ofName(JobConventions.batchSizeAttributeName),
                "1024",
                batchSizeInfo)

            channelField(
                "Capacity",
                AttributePath.ofName(JobConventions.capacityAttributeName),
                "0",
                capacityInfo)
        }
    }


    private fun ChildrenBuilder.channelField(
        fieldLabel: String,
        path: AttributePath,
        fallback: String,
        info: String
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 0.25.em
            }

            div {
                css { flexGrow = number(1.0) }
                JobChannelNumberField::class.react {
                    label = fieldLabel
                    objectLocation = props.mainLocation
                    attributePath = path
                    fallbackValue = fallback
                    clientStateGlobal = props.clientStateGlobal
                    mirroredGraphStore = props.mirroredGraphStore
                }
            }

            infoIcon(info)
        }
    }


    // Neutral [i] with the explanation as a hover tooltip (mirrors StepHeader's validation-icon idiom).
    private fun ChildrenBuilder.infoIcon(text: String) {
        Tooltip {
            title = ReactNode(text)

            span {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                }

                icon("material-symbols:info-outline") {
                    style = unsafeJso {
                        color = Color("rgba(0, 0, 0, 0.45)")
                        fontSize = 1.1.em
                    }
                }
            }
        }
    }
}
