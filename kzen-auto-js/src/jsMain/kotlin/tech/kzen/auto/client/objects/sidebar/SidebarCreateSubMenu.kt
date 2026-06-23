package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.coroutines.delay
import mui.material.Menu
import mui.material.MenuItem
import mui.material.PopoverOrigin
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import web.cssom.*
import web.html.HTMLElement


//---------------------------------------------------------------------------------------------------------------------
// One declarative create-group rendered as a hover flyout. The trigger MenuItem (group title + chevron) lives in the
// parent create menu; hovering it opens a nested Menu anchored to its right edge holding the group's "New ..." items.
//
// MUI v6 has no built-in nested MenuItem, so the flyout is composed manually. Two details make nesting two Modal-backed
// Menus behave:
//   1. Pointer bridge: the nested Menu's invisible backdrop would otherwise swallow hover/click over the parent menu,
//      so the nested root is pointerEvents:none (click/hover-through) and pointer events are re-enabled only on the
//      flyout content. A short close delay (generation-guarded, no clearTimeout) lets the cursor cross the trigger →
//      flyout seam without the flyout unmounting from under it.
//   2. Focus: the nested Menu is a separate portal, so letting it grab/enforce focus starts a tug-of-war with the
//      parent Modal's focus enforcement. disableAutoFocus*/disableEnforceFocus keep the nested menu from moving focus.
external interface SidebarCreateSubMenuProps: Props {
    var title: String
    var groupIcon: String

    // closes the whole create menu (the parent SidebarItemMenu) — invoked after a grouped item is chosen
    var parentClose: () -> Unit

    // NB: plain (non-receiver) function — external declarations can't hold a receiver function type. The host applies
    //     it to the supplied ChildrenBuilder; the `close` it hands back dismisses BOTH this flyout and the parent menu.
    var renderItems: (childrenBuilder: ChildrenBuilder, close: () -> Unit) -> Unit
}


external interface SidebarCreateSubMenuState: State {
    var open: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class SidebarCreateSubMenu(
    props: SidebarCreateSubMenuProps
):
    RComponent<SidebarCreateSubMenuProps, SidebarCreateSubMenuState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // grace period for the cursor to cross the trigger → flyout seam before a pending close takes effect
        private const val closeDelayMillis = 120L
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The trigger element captured on hover/click — the flyout anchors to it. Held as a plain field (not React state):
    // it's assigned immediately before the open setState, so it's current by the time render reads it.
    private var anchorElement: HTMLElement? = null

    // Bumped on every open/keep/close intent. A deferred close captures the value at scheduling time and only fires if
    // it's still current — so a re-entry (which bumps the counter) cancels the pending close without any clearTimeout.
    private var intentGeneration = 0

    private var disposed = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarCreateSubMenuState.init(props: SidebarCreateSubMenuProps) {
        open = false
    }


    override fun componentWillUnmount() {
        disposed = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun openWith(element: HTMLElement) {
        intentGeneration += 1
        anchorElement = element
        setState {
            open = true
        }
    }


    // cancel any pending close without re-rendering (the flyout is already open)
    private fun keepOpen() {
        intentGeneration += 1
    }


    private fun scheduleClose() {
        intentGeneration += 1
        val generation = intentGeneration
        async {
            delay(closeDelayMillis)
            if (!disposed && generation == intentGeneration) {
                setState {
                    open = false
                }
            }
        }
    }


    private fun closeNow() {
        intentGeneration += 1
        setState {
            open = false
        }
    }


    // dismiss this flyout AND the parent create menu — used when a grouped item is chosen
    private fun closeAll() {
        closeNow()
        props.parentClose()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val iconStyle: CSSProperties = unsafeJso {
            marginRight = 1.em
        }

        MenuItem {
            onMouseEnter = { event ->
                openWith(event.currentTarget)
            }
            onMouseLeave = {
                scheduleClose()
            }
            // open on click too, so the group is reachable by keyboard/touch where hover isn't available
            onClick = { event ->
                openWith(event.currentTarget)
            }

            icon(props.groupIcon) {
                style = iconStyle
            }

            div {
                css {
                    flexGrow = number(1.0)
                }
                +props.title
            }

            icon("material-symbols:chevron-right") {}
        }

        Menu {
            open = state.open
            onClose = ::closeNow
            anchorEl = anchorElement?.let { { _ -> it } }
            anchorOrigin = anchorRight
            transformOrigin = transformLeft

            // a hover flyout in its own portal must not move or trap focus (would fight the parent Modal); nor re-lock
            // body scroll (already locked by the parent menu)
            disableAutoFocus = true
            disableAutoFocusItem = true
            disableEnforceFocus = true
            disableScrollLock = true

            // make the nested backdrop click/hover-through to the parent menu; re-enable pointer events on the content
            sx {
                pointerEvents = None.none
            }

            div {
                css {
                    pointerEvents = Auto.auto
                }
                onMouseEnter = { keepOpen() }
                onMouseLeave = { scheduleClose() }

                props.renderItems(this, ::closeAll)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val anchorRight: PopoverOrigin get() = unsafeJso {
        asDynamic().vertical = "top"
        asDynamic().horizontal = "right"
    }

    private val transformLeft: PopoverOrigin get() = unsafeJso {
        asDynamic().vertical = "top"
        asDynamic().horizontal = "left"
    }
}
