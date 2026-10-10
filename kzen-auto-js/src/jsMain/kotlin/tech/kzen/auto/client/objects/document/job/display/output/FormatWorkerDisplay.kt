package tech.kzen.auto.client.objects.document.job.display.output

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayDefault
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayProps
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayWrapper
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface FormatWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface FormatWorkerDisplayState: State {
    // Whether `groupBy` holds a value, read from notation
    var grouped: Boolean
}


/**
 * The ordinary Worker card, saying in its header how many records `Format` encoded into how many bytes (and groups),
 * with "Write each file" shown only once Group by is set: without groups there is one file, written at the end.
 */
@Suppress("unused")
class FormatWorkerDisplay(
    props: FormatWorkerDisplayProps
):
    RPureComponent<FormatWorkerDisplayProps, FormatWorkerDisplayState>(props),
    ClientStateGlobal.Observer
{
    companion object {
        private val groupByAttributeName = AttributeName("groupBy")
        private val groupEndAttributeName = AttributeName("groupEnd")
    }


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
            FormatWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    override fun FormatWorkerDisplayState.init(props: FormatWorkerDisplayProps) {
        grouped = false
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    // Declared by hand because the props hold the location under `common`, not as ObjectScopedProps
    override fun observedObjectLocation(): ObjectLocation = props.common.objectLocation


    override fun onClientState(clientState: ClientState) {
        val groupBy = clientState
            .graphStructure()
            .graphNotation
            .firstAttribute(props.common.objectLocation, groupByAttributeName)
            as? ScalarAttributeNotation
        val grouped = !groupBy?.value.isNullOrBlank()
        if (state.grouped != grouped) {
            setState {
                this.grouped = grouped
            }
        }
    }


    override fun ChildrenBuilder.render() {
        WorkerDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common
            hiddenAttributes = if (state.grouped) setOf() else setOf(groupEndAttributeName)
            statusText = OutputProgressText::format
        }
    }
}
