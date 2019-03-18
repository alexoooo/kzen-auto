package tech.kzen.auto.client.objects.sidebar

import kotlinx.css.em
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


class SidebarController(
        props: Props
):
        RComponent<SidebarController.Props, SidebarController.State>(props),
        ModelManager.Observer,
        NavigationManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val bundleBase = "main"
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
//            var onNavigation: ((BundlePath?) -> Unit)?
    ): RProps


    class State(
            var structure: GraphStructure?,
            var bundlePath: BundlePath?
    ): RState


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
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.modelManager.observe(this)
            ClientContext.navigationManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        val structure = state.structure
                ?: return

        val mainBundles = mainBundles(structure)

        if (state.bundlePath == null && ! mainBundles.isEmpty()) {
            ClientContext.navigationManager.goto(mainBundles[0])
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: GraphStructure, event: NotationEvent?) {
//        console.log("^ handleModel")
        setState {
            structure = autoModel
        }
    }


    override fun handleNavigation(bundlePath: BundlePath?) {
        setState {
            this.bundlePath = bundlePath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun generateBundleName(): BundlePath {
//        val prefix = "Script"
//
//        val suffix = findSuffix(prefix, state.structure)
//
//        return resolve(prefix + suffix)
//    }


//    private fun findSuffix(
//            prefix: String,
//            structure: GraphStructure?
//    ): String {
//        if (structure == null) {
//            return "-" + Random.nextInt()
//        }
//        else {
//            for (i in 2 .. 999) {
//                val candidateSuffix = "-$i"
//                val candidatePath = resolve(prefix + candidateSuffix)
//
//                if (structure.graphNotation.bundles.values.containsKey(candidatePath)) {
//                    continue
//                }
//
//                return candidateSuffix
//            }
//
//            return "-" + Random.nextInt()
//        }
//    }


//    private fun resolve(name: String): BundlePath {
//        return BundlePath(listOf(bundleBase, "$name.yaml"))
//    }


//    private suspend fun createBundle(bundlePath: BundlePath) {
//        ClientContext.commandBus.apply(CreateBundleCommand(bundlePath))
//    }


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

            child(SidebarFolder::class) {
                attrs {
                    this.structure = structure
                    selectedBundlePath = state.bundlePath
                }
            }


//            styledSpan {
//                attrs {
//                    title = "Add new file"
//                }
//
//                child(MaterialIconButton::class) {
//                    attrs {
//                        onClick = {
//                            onAdd()
//                        }
//                    }
//
//                    child(AddCircleOutlineIcon::class) {}
//                }
//            }
        }
    }
}