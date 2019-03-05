package tech.kzen.auto.client.objects

import kotlinx.css.Cursor
import kotlinx.css.em
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.*
import react.dom.div
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.DeleteIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.CreateBundleCommand
import tech.kzen.lib.common.structure.notation.edit.DeleteBundleCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import kotlin.random.Random


class SidebarController(
        props: SidebarController.Props
):
        RComponent<SidebarController.Props, SidebarController.State>(props),
        ModelManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val bundleBase = "main"
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var onNavigation: ((BundlePath?) -> Unit)?
    ): RProps


    class State(
            var structure: GraphStructure?,
            var bundlePath: BundlePath?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.modelManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: SidebarController.Props,
            prevState: SidebarController.State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)

        val structure = state.structure
        if (structure != null) {
//            &&
//            state.bundlePath == null &&
//                    (prevState.structure == null ||
//                            prevState.structure!!.graphNotation.bundles.values[props.bundlePath] != null)

            val mainBundles = mainBundles(structure)

            if (mainBundles.isEmpty()) {
                async {
                    createBundle(BundlePath(listOf("main", "Script.yaml")))
                }
            }
            else if (state.bundlePath == null && ! mainBundles.isEmpty()) {
                setState {
                    bundlePath = mainBundles[0]
                }
            }

            if (state.bundlePath != prevState.bundlePath) {
                props.onNavigation?.invoke(state.bundlePath)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: GraphStructure, event: NotationEvent?) {
//        console.log("^ handleModel")
        setState {
            structure = autoModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generateBundleName(): BundlePath {
        val prefix = "Script"

        val suffix = findSuffix(prefix, state.structure)

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
        async {
            val newBundleName = generateBundleName()
            createBundle(newBundleName)
        }
    }


    private fun onRemove(bundlePath: BundlePath) {
        async {
            ClientContext.commandBus.apply(DeleteBundleCommand(bundlePath))
        }
    }


    private fun onSelect(bundlePath: BundlePath) {
        async {
            ClientContext.commandBus.apply(DeleteBundleCommand(bundlePath))
        }
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


    private fun displayPath(bundlePath: BundlePath): String {
        val path = bundlePath.segments.subList(1, bundlePath.segments.size - 1)
        val suffix = bundlePath.segments.last()

        val parts = path.plus(suffix.substringBeforeLast("."))

        return parts.joinToString("/")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val structure = state.structure
                ?: return

        styledDiv {
            css {
                paddingTop = 1.em
                paddingRight = 1.em
                paddingBottom = 1.em
                paddingLeft = 1.em
            }

            val mainBundlePaths = mainBundles(structure)
            val current = state.bundlePath

            if (mainBundlePaths.isEmpty()) {
                +"Please create a file"
            }
            else {
                for (bundlePath in mainBundlePaths) {
                    div {
                        if (bundlePath == current) {
                            +"> "
                        }
                        else {
                            +"- "
                        }

                        styledSpan {
                            css {
                                cursor = Cursor.pointer
                            }

                            attrs {
                                onClickFunction = {
                                    setState {
                                        this.bundlePath = bundlePath
                                    }
                                }
                            }

                            +displayPath(bundlePath)
                        }

                        styledSpan {
                            attrs {
                                title = "Remove file"
                            }

                            child(MaterialIconButton::class) {
                                attrs {
                                    onClick = {
                                        onRemove(bundlePath)
                                    }
                                }

                                child(DeleteIcon::class) {}
                            }
                        }
                    }
                }
            }

            styledSpan {
                attrs {
                    title = "Add new file"
                }

                child(MaterialIconButton::class) {
                    attrs {
//                        style = reactStyle {
//                            if (! state.creating) {
//                                opacity = 0
//                                cursor = Cursor.default
//                            }
//                        }

                        onClick = {
                            onAdd()
                        }
                    }

                    child(AddCircleOutlineIcon::class) {}
                }
            }
        }
    }
}