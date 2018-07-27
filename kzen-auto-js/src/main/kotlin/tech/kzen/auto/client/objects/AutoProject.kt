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
import tech.kzen.lib.common.notation.model.*


@Suppress("unused")
class AutoProject: RComponent<RProps, AutoProject.State>() {
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var notation: ProjectNotation?,
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
            val autoExecutor = AutoExecutor.of(projectNotation)

            setState {
                notation = projectNotation
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

                    for (e in projectPackage.objects) {
                        renderAction(e.key, e.value)

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
            objectNotation: ObjectNotation
    ) {
        child(ActionController::class) {
            attrs {
                name = objectName
                notation = objectNotation
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
