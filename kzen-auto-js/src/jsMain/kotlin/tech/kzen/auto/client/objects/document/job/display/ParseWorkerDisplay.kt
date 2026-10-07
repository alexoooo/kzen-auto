package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.common.file.FileRowStatus
import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.JobReadConventions
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


external interface ParseWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface ParseWorkerDisplayState: State {
    // Whether `role` holds a value, read from notation: a stale part name stays visible so it can be cleared.
    var roleSet: Boolean
}


/**
 * The ordinary Worker card, saying what Parse found and how far it has got, from its own validation details and
 * progress ([JobReadConventions] plus its own `emitted`):
 *
 * - "Part to read" shows only when it applies: the input carries units holding several named parts (or a part is
 *   already named). For files there is nothing to choose.
 * - Under the format (moved to the top of the card), the format automatic detection picked: per sampled file
 *   before Run, for the file being read during it. Shown only when the validation reported a detection, which it
 *   does only for Automatic.
 * - The header gives the file being read, its bytes read of its size, and the rows sent.
 */
@Suppress("unused")
class ParseWorkerDisplay(
    props: ParseWorkerDisplayProps
):
    RPureComponent<ParseWorkerDisplayProps, ParseWorkerDisplayState>(props),
    ClientStateGlobal.Observer
{
    companion object {
        private val roleAttributeName = AttributeName("role")
        private val formatAttributeName = AttributeName("format")
        private const val emittedKey = "emitted"
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
            ParseWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    override fun ParseWorkerDisplayState.init(props: ParseWorkerDisplayProps) {
        roleSet = false
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
        val role = clientState
            .graphStructure()
            .graphNotation
            .firstAttribute(props.common.objectLocation, roleAttributeName)
            as? ScalarAttributeNotation
        val roleSet = ! role?.value.isNullOrBlank()
        if (state.roleSet != roleSet) {
            setState {
                this.roleSet = roleSet
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
            hiddenAttributes =
                if (partApplies()) setOf(formatAttributeName)
                else setOf(formatAttributeName, roleAttributeName)
            bodyBefore = { bodyBuilder -> bodyBuilder.renderFormat() }
            statusText = ::readStatusText
        }
    }


    private fun details(): Map<String, Any?> =
        props.common.validation?.details.orEmpty()


    // Units whose part names were not sampled (a Logic source before Run) may still hold several, so the field stays
    private fun partApplies(): Boolean {
        val details = details()
        val roles = details[JobReadConventions.rolesKey] as? List<*>
        return state.roleSet ||
            details[JobReadConventions.readsUnitsKey] == true && (roles.isNullOrEmpty() || roles.size > 1)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // "measurements.txt · 1.2 GB / 1.4 GB (86%) · 723,968 rows"; null (the generic counts) before anything is read
    private fun readStatusText(progress: JobWorkerProgress?): String? {
        if (progress == null) {
            return null
        }

        val parts = mutableListOf<String>()
        progress.status?.let { parts.add(it) }

        val name = progress.progressMap[JobReadConventions.readNameKey] as? String
        if (name != null) {
            parts.add(listOfNotNull(name, bytesText(progress)).joinToString(" · "))
        }
        else {
            progress.longValue(JobReadConventions.readDoneKey)?.let { parts.add("${FormatUtils.decimalSeparator(it)} read") }
        }

        progress.longValue(emittedKey)?.let { parts.add("${FormatUtils.decimalSeparator(it)} rows") }
        return parts.joinToString(" · ").takeIf { parts.isNotEmpty() }
    }


    private fun bytesText(progress: JobWorkerProgress): String? {
        val bytes = progress.longValue(JobReadConventions.readBytesKey)
        val size = progress.longValue(JobReadConventions.readSizeKey)
        return when {
            bytes != null && size != null && size > 0 -> {
                val percent = FileRowStatus.percentOf(bytes, size)
                "${FormatUtils.readableFileSize(bytes)} / ${FormatUtils.readableFileSize(size)} ($percent%)"
            }
            bytes != null -> FormatUtils.readableFileSize(bytes)
            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Format comes first, rehomed from the generic editors so what was detected reads directly under it
    private fun ChildrenBuilder.renderFormat() {
        div {
            css {
                marginBottom = 0.5.em
            }
            props.attributeEditorManager.child(this) {
                objectLocation = props.common.objectLocation
                attributeName = formatAttributeName
            }
        }
        renderDetected()
    }


    private fun ChildrenBuilder.renderDetected() {
        val detected = (details()[JobReadConventions.detectedFormatsKey] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.mapNotNull { file ->
                val name = file[JobReadConventions.detectedFileKey] as? String ?: return@mapNotNull null
                val label = file[JobReadConventions.detectedLabelKey] as? String ?: return@mapNotNull null
                name to label
            }
            ?.takeIf { it.isNotEmpty() }
            ?: return

        val progressMap = props.common.progress?.progressMap.orEmpty()
        val liveFormat = progressMap[JobReadConventions.readFormatKey] as? String
        val liveName = progressMap[JobReadConventions.readNameKey] as? String
        val lines =
            if (liveFormat != null) {
                listOf("Detected: $liveFormat" + (liveName?.let { " ($it)" } ?: ""))
            }
            else if (detected.map { it.second }.distinct().size == 1) {
                listOf("Detected: ${detected.first().second}")
            }
            else {
                listOf("Detected:") + detected.map { (name, label) -> "$name: $label" }
            }

        div {
            css {
                marginTop = (-0.25).em
                marginBottom = 0.5.em
                fontSize = 0.85.em
                color = Color("rgba(0, 0, 0, 0.65)")
            }
            for ((index, line) in lines.withIndex()) {
                div {
                    key = Key("$index")
                    if (index > 0) {
                        css { paddingLeft = 1.em }
                    }
                    +line
                }
            }
        }
    }
}
