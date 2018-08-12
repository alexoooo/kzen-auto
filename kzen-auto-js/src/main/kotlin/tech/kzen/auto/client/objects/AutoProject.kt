package tech.kzen.auto.client.objects

import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.br
import react.dom.div
import react.dom.input
import react.dom.pre
import tech.kzen.auto.client.service.*
import tech.kzen.auto.client.ui.ActionController
import tech.kzen.auto.client.ui.ActionCreator
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.common.service.ProjectModel
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.*


@Suppress("unused")
class AutoProject: RComponent<RProps, AutoProject.State>(), ModelManager.Subscriber {
    //-----------------------------------------------------------------------------------------------------------------
    // todo: manage dynamically
    val projectPath = ProjectPath("notation/dummy/dummy.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var notation: ProjectNotation?,
            var metadata: GraphMetadata?/*,
            var executor: AutoExecutor?*/
    ) : RState


    @Suppress("unused")
    class Wrapper: ReactWrapper {
        override fun execute(input: RBuilder): ReactElement {
            return input.child(AutoProject::class) {}
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("AutoProject - Subscribed")
        ClientContext.modelManager.subscribe(this)
    }


    override fun componentWillUnmount() {
//        println("AutoProject - Un-subscribed")
        ClientContext.modelManager.unsubscribe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handle(autoModel: ProjectModel) {
        println("AutoProject - && handled - ${autoModel.projectNotation.packages[projectPath]!!.objects.keys}")

//        async {
            setState {
                notation = autoModel.projectNotation
                metadata = autoModel.graphMetadata
            }
//        }
    }


    private fun onReload() {
        async {
            ClientContext.modelManager.refresh()
        }
    }


    private fun onRunAll() {

    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        div(classes = "child") {
            val projectNotation = state.notation
            if (projectNotation == null) {
                +"Loading..."
            }
            else {
                println("AutoProject - Available packages: ${projectNotation.packages.keys}")

                val projectPackage: PackageNotation? =
                        projectNotation.packages[projectPath]

                if (projectPackage == null) {
                    +"Please provide project package"
                }
                else {
                    println("AutoProject - the package - ${projectPackage.objects.keys}")

                    +"Action sequence:"
                    val graphMetadata = state.metadata!!

                    for (e in projectPackage.objects) {
                        renderAction(e.key, projectNotation, graphMetadata)

                        pre {
                            +"|\n"
                            +"V"
                        }
                    }

                    div {
                        child(ActionCreator::class) {
                            attrs {
                                notation = projectNotation
                                path = projectPath
                            }
                        }
                    }

                    br {}

                    div {
                        renderRunAll()
                        renderRefresh()
                    }
                }
            }
        }
    }


    private fun RBuilder.renderAction(
            objectName: String,
            projectNotation: ProjectNotation,
            graphMetadata: GraphMetadata
//            objectNotation: ObjectNotation,
//            objectMetadata: ObjectMetadata
    ) {
        child(ActionController::class) {
            key = objectName

            attrs {
                name = objectName
//                notation = objectNotation
//                metadata = objectMetadata
                notation = projectNotation
                metadata = graphMetadata
//                executor = state.executor!!
            }
        }
    }


    private fun RBuilder.renderRunAll() {
        input (type = InputType.button) {
            attrs {
                value = "Run All"
                onClickFunction = { onRunAll() }
            }
        }
    }


    private fun RBuilder.renderRefresh() {
        input (type = InputType.button) {
            attrs {
                value = "Refresh"
                onClickFunction = { onReload() }
            }
        }
    }
}
