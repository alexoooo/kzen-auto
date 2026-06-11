package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import mui.material.IconButton
import mui.material.Menu
import mui.system.sx
import react.ChildrenBuilder
import react.RefObject
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import web.cssom.*
import web.html.HTMLElement


//---------------------------------------------------------------------------------------------------------------------
// Shared options (⋮) menu for a sidebar item (folder or file). Owns the open/close state, the menu anchor, and the
// hover-revealed icon; the host supplies only the menu's tooltip and items (each wired to the injected close()).
//
// The icon is revealed on hover via pure CSS rather than a hover-state field: a state toggle re-renders the host
// row on every mouse move and can leave the icon stuck hidden after a click-away (the menu backdrop swallows the
// boundary-crossing mouseover that would re-reveal it). The host row must therefore carry the revealOnHoverSelector
// rule (see usage in SidebarFolder / SidebarFile) — the reveal trigger is the whole row, which the host owns.
external interface SidebarItemMenuProps: react.Props {
    var title: String

    // NB: plain (non-receiver) function — external declarations can't hold a receiver function type. The host
    //     applies it to the supplied ChildrenBuilder (e.g. childrenBuilder.renderMenuItems(close)).
    var renderItems: (childrenBuilder: ChildrenBuilder, close: () -> Unit) -> Unit
}


external interface SidebarItemMenuState: react.State {
    var optionsOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class SidebarItemMenu(
    props: SidebarItemMenuProps
):
    RComponent<SidebarItemMenuProps, SidebarItemMenuState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // The host row adds this rule to its hover css to fade the ⋮ icon in. Paired with the [data-options-button]
        // attribute set below; kept here so the selector and the marker can't drift apart.
        @Suppress("ConstPropertyName")
        const val revealOnHoverSelector = "&:hover [data-options-button]"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var anchorRef: RefObject<HTMLElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarItemMenuState.init(props: SidebarItemMenuProps) {
        optionsOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun openMenu() {
        setState {
            optionsOpen = true
        }
    }


    private fun closeMenu() {
        setState {
            optionsOpen = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.absolute
                top = 0.px
                right = 0.px
            }
            ref = anchorRef

            span {
                asDynamic()["data-options-button"] = ""

                css {
                    // NB: blinks in and out without this
                    backgroundColor = Color.transparent

                    // NB: hidden by default; the host row's revealOnHoverSelector rule reveals it. Stays visible
                    //     while the menu is open so it doesn't vanish under the menu backdrop.
                    opacity = if (state.optionsOpen) number(1.0) else number(0.0)
                }

                IconButton {
                    title = props.title
                    onClick = { openMenu() }

                    sx {
                        marginTop = (-13).px
                        marginRight = (-16).px
                    }

                    icon("material-symbols:more-vert") {}
                }
            }

            Menu {
                open = state.optionsOpen
                onClose = ::closeMenu
                anchorEl = anchorRef.current?.let { { _ -> it } }
                renderItems()
            }
        }
    }


    private fun ChildrenBuilder.renderItems() {
        props.renderItems(this, ::closeMenu)
    }
}
