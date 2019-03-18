package tech.kzen.auto.client.objects.sidebar

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.HTMLButtonElement
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.CreateBundleCommand
import kotlin.random.Random


class SidebarFolder(
        props: Props
):
        RComponent<SidebarFolder.Props, SidebarFolder.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val bundleBase = "main"
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var structure: GraphStructure/*,
            var onNavigation: ((BundlePath?) -> Unit)?*/
    ): RProps


    class State(
            var hoverItem: Boolean,
            var hoverOptions: Boolean,
            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var buttonRef: HTMLButtonElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarFolder.State.init(props: SidebarFolder.Props) {
        optionsOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainBundles(graphStructure: GraphStructure): List<BundlePath> {
        return graphStructure
                .graphNotation
                .bundles
                .values
                .keys
                .filter { it.segments[0] == bundleBase }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(itemOrMenu: Boolean) {
        if (itemOrMenu) {
            setState {
                hoverItem = true
            }
        }
        else {
            setState {
                hoverOptions = true
            }
        }
    }


    private fun onMouseOut(itemOrMenu: Boolean) {
        if (itemOrMenu) {
            setState {
                hoverItem = false
            }
        }
        else {
            setState {
                hoverOptions = false
            }
        }
    }


    private fun onOptionsToggle() {
        setState {
            optionsOpen = ! optionsOpen
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false
            hoverItem = false
            hoverOptions = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generateBundleName(): BundlePath {
        val prefix = "Script"

        val suffix = findSuffix(prefix, props.structure)

        return resolve(prefix + suffix)
    }


    private fun findSuffix(
            prefix: String,
            structure: GraphStructure?
    ): String {
        if (structure == null) {
            return "-" + Random.nextInt()
        }
        else {
            for (i in 2 .. 999) {
                val candidateSuffix = "-$i"
                val candidatePath = resolve(prefix + candidateSuffix)

                if (structure.graphNotation.bundles.values.containsKey(candidatePath)) {
                    continue
                }

                return candidateSuffix
            }

            return "-" + Random.nextInt()
        }
    }


    private fun resolve(name: String): BundlePath {
        return BundlePath(listOf(bundleBase, "$name.yaml"))
    }


    private suspend fun createBundle(bundlePath: BundlePath) {
        ClientContext.commandBus.apply(CreateBundleCommand(bundlePath))
    }


    private fun onAdd() {
        onOptionsClose()

        async {
            val newBundleName = generateBundleName()
            createBundle(newBundleName)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val mainBundlePaths = mainBundles(props.structure)

        renderFolderItem()

        renderSubItems(mainBundlePaths)
    }


    private fun RBuilder.renderFolderItem() {
        styledDiv {
            css {
                position = Position.relative
                height = 2.em
                width = 100.pct

//                backgroundColor = Color.red
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(true)
                }

                onMouseOutFunction = {
                    onMouseOut(true)
                }
            }

//            val iconWidth = 22.px
            val iconWidth = 24.px

            styledDiv {
                css {
                    position = Position.absolute
//                    width = 100.pct
                    top = 0.px
                    left = 0.px

                    height = iconWidth
                }

                child(FolderOpenIcon::class) {}
            }

            styledDiv {
                css {
                    position = Position.absolute
//                    width = 100.pct
                    top = 0.px
                    left = iconWidth
                    width = 100.pct.minus(iconWidth)
                    marginLeft = 6.px

                    fontSize = (1.2).em
                }

                +"Project"
            }

            styledDiv {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px

//                    backgroundColor = Color.blue
                }

                renderOptionsMenu()
            }
        }
    }


    private fun RBuilder.renderSubItems(
            mainBundlePaths: List<BundlePath>
    ) {
//        val current = state.bundlePath
        for (bundlePath in mainBundlePaths) {
            child(SidebarFile::class) {
                attrs {
                    this.bundlePath = bundlePath
                }
            }
        }
    }


    private fun RBuilder.renderOptionsMenu() {
        styledSpan {
            css {
                // NB: blinks in and out without this
                backgroundColor = Color.transparent

                if (! (state.hoverItem || state.hoverOptions)) {
                    display = Display.none
                }
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(false)
                }

                onMouseOutFunction = {
                    onMouseOut(false)
                }
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Options..."
                    onClick = ::onOptionsToggle

                    buttonRef = {
                        this@SidebarFolder.buttonRef = it
                    }

                    style = reactStyle {
                        marginTop = (-13).px
                        marginRight = (-16).px
                    }
                }

                child(MoreVertIcon::class) {}
            }
        }

        child(MaterialMenu::class) {
            attrs {
                open = state.optionsOpen

                onClose = ::onOptionsClose

                anchorEl = buttonRef
            }

            renderMenuItems()
        }
    }


    private fun RBuilder.renderMenuItems() {
        val iconStyle = reactStyle {
            marginRight = 1.em
        }

        child(MaterialMenuItem::class) {
            attrs {
                onClick = ::onAdd
//                onClick = ::onOptionsClose
            }
            child(PlaylistPlayIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"New Script..."
        }

        child(MaterialMenuItem::class) {
            attrs {
//                onClick = ::onShiftUp
                onClick = ::onOptionsClose
            }
            child(TableChartIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"New Pivot Table..."
        }
    }
}