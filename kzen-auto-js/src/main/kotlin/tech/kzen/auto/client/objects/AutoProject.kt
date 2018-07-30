package tech.kzen.auto.client.objects

import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.br
import react.dom.div
import react.dom.input
import react.dom.pre
import tech.kzen.auto.client.service.AutoExecutor
import tech.kzen.auto.client.service.AutoModelService
import tech.kzen.auto.client.ui.ActionController
import tech.kzen.auto.client.ui.ActionCreator
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.notation.model.*


@Suppress("unused")
class AutoProject: RComponent<RProps, AutoProject.State>() {
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var notation: ProjectNotation?,
            var metadata: GraphMetadata?,
            var executor: AutoExecutor?
    ) : RState


    @Suppress("unused")
    class Wrapper: ReactWrapper {
        override fun execute(input: RBuilder): ReactElement {
            return input.child(AutoProject::class) {}
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var mounted = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        mounted = true
        loadData()
    }


    override fun componentWillUnmount() {
        mounted = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun loadData() {
        if (! mounted) {
            return
        }

        async {
            val projectNotation = AutoModelService.projectNotation()

            val graphMetadata = AutoModelService.metadata(projectNotation)
            val objectGraph = AutoModelService.graph(projectNotation, graphMetadata)
            val autoExecutor = AutoExecutor(objectGraph)

            setState {
                notation = projectNotation
                metadata = graphMetadata
                executor = autoExecutor
            }
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
                println("!! Available packages: ${projectNotation.packages.keys}")

                // todo: manage dynamically
                val projectPath = ProjectPath("notation/dummy/dummy.yaml")

                val projectPackage: PackageNotation? =
                        projectNotation.packages[projectPath]

                if (projectPackage == null) {
                    +"Please provide project package"
                }
                else {
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
            attrs {
                name = objectName
//                notation = objectNotation
//                metadata = objectMetadata
                notation = projectNotation
                metadata = graphMetadata
                executor = state.executor!!
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
}
