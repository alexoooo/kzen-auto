package tech.kzen.auto.client.objects.document.common.file

import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.Color
import web.cssom.NamedColor
import web.cssom.em


/**
 * The control that shows and hides a [FileBrowser], wherever it ends up being drawn.
 *
 * One button in two homes — a card header when a header claims it, the top of the editor otherwise — so its look is
 * shared rather than kept in step by hand. Filled while open, in Report's own toggle palette.
 */
internal fun ChildrenBuilder.fileBrowserToggle(open: Boolean, onToggle: () -> Unit) {
    Button {
        variant = ButtonVariant.outlined
        size = Size.small

        sx {
            if (open) {
                backgroundColor = toggleSelectedColor
            }
            color = NamedColor.black
            borderColor = toggleBorderColor
        }

        title = if (open) "Hide browser" else "Show browser"
        onClick = { onToggle() }

        icon("material-symbols:folder-open") {
            style = unsafeJso { marginRight = 0.25.em }
        }

        +"Browser"
    }
}


private val toggleSelectedColor = Color("#e0e0e0")
private val toggleBorderColor = Color("#777777")


object FileBrowserToggleKey: BridgeKey<FileBrowserToggleChannel> {
    override fun create() = FileBrowserToggleChannel()
}


/**
 * Whether a card's file browser is showing, shared between the card's header and the editor in its body.
 *
 * The toggle reads best in the title bar — that is where Report puts it, and where it stops competing with the
 * selection for the top of the card — but the browser it shows is drawn by an attribute editor mounted generically
 * through `AttributeEditorManager`, whose props are fixed at `{objectLocation, attributeName}`. Neither side can
 * hand the other a callback, and widening that contract for one Worker's convenience would put a file-selection
 * concern in the framework every attribute editor goes through.
 *
 * So they meet where [tech.kzen.auto.client.objects.document.bridge.DocumentBridge] already exists for siblings
 * with no shared parent state. A header claims a card by [host]ing it: until one does, the editor keeps drawing its
 * own inline toggle, which is what a plain `FileDataSource` card and the legacy `paths` attribute still get.
 *
 * Openness is keyed by card, not by attribute — a card hoists one browser into its header, and a second
 * file-selection attribute on the same object would keep its toggle inline where it is unambiguous.
 */
class FileBrowserToggleChannel {
    interface Observer {
        fun onFileBrowserToggled(objectLocation: ObjectLocation)
    }


    private val hosts = mutableSetOf<ObjectLocation>()
    private val open = mutableSetOf<ObjectLocation>()
    private val observers = mutableMapOf<ObjectLocation, MutableSet<Observer>>()


    /** Claim this card's toggle for a header. Idempotent, so it is safe to re-assert on every render. */
    fun host(objectLocation: ObjectLocation) {
        hosts.add(objectLocation)
    }


    fun unhost(objectLocation: ObjectLocation) {
        hosts.remove(objectLocation)
        open.remove(objectLocation)
    }


    fun hosted(objectLocation: ObjectLocation): Boolean {
        return objectLocation in hosts
    }


    fun isOpen(objectLocation: ObjectLocation): Boolean {
        return objectLocation in open
    }


    fun setOpen(objectLocation: ObjectLocation, value: Boolean) {
        val changed =
            if (value) {
                open.add(objectLocation)
            }
            else {
                open.remove(objectLocation)
            }

        if (changed) {
            observers[objectLocation]?.toList()?.forEach { it.onFileBrowserToggled(objectLocation) }
        }
    }


    fun toggle(objectLocation: ObjectLocation) {
        setOpen(objectLocation, ! isOpen(objectLocation))
    }


    fun observe(objectLocation: ObjectLocation, observer: Observer) {
        observers.getOrPut(objectLocation) { mutableSetOf() }.add(observer)
    }


    fun unobserve(objectLocation: ObjectLocation, observer: Observer) {
        val remaining = observers[objectLocation]
            ?: return
        remaining.remove(observer)
        if (remaining.isEmpty()) {
            observers.remove(objectLocation)
        }
    }
}
