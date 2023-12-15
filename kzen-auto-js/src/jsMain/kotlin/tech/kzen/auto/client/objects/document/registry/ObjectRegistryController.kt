package tech.kzen.auto.client.objects.document.registry

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.hr
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.registry.ObjectRegistryConventions
import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryReflection
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.NamedColor
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface ObjectRegistryControllerState: State {
    var objectLocation: ObjectLocation?
    var classes: ClassListSpec?
    var reflection: List<ObjectRegistryReflection>?
    var lastError: String?
}


class ObjectRegistryController:
    RPureComponent<Props, ObjectRegistryControllerState>(),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ObjectRegistryControllerState.init(props: Props) {
        objectLocation = null
        classes = null
        reflection = null
        lastError = null
    }


    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! ObjectRegistryConventions.isObjectRegistry(documentNotation)) {
            return
        }

        val objectLocation = documentPath.toMainObjectLocation()
        val classes = ObjectRegistryConventions.classesSpec(documentNotation)

        if (state.objectLocation == objectLocation &&
                state.classes == classes) {
            return
        }

        setState {
            this.objectLocation = objectLocation
            this.classes = classes
        }

        async {
            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val result = loadReflection(objectLocation)

            setState {
                reflection = result.valueOrNull()
                lastError = result.errorOrNull()
            }
        }
    }



    private suspend fun loadReflection(objectLocation: ObjectLocation): ClientResult<List<ObjectRegistryReflection>> {
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val result = ClientContext.restClient.performDetached(objectLocation)

        return when (result) {
            is ExecutionSuccess -> {
                val resultNotation = result.value as ListExecutionValue
                val resultValue = ObjectRegistryReflection.listOfExecutionValue(resultNotation)
                ClientResult.ofSuccess(resultValue)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
//        private val stepDisplayManager: StepDisplayManager.Wrapper,
//        private val sequenceCommander: SequenceCommander,
//        private val ribbonController: RibbonController.Wrapper
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    ObjectRegistryController::class.react {
//                        this.stepDisplayManager = this@Wrapper.stepDisplayManager
//                        this.sequenceCommander = this@Wrapper.sequenceCommander
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val objectLocation = state.objectLocation ?: return
        val classes = state.classes ?: return

        div {
            css {
                padding = 1.em
            }

            val lastError = state.lastError
            if (lastError != null) {
                div {
                    css {
                        color = NamedColor.red
                    }
                    +"Error: $lastError"
                }
            }

            val reflection = state.reflection

            for ((index, className) in classes.classNames.withIndex()) {
                div {
                    key = className.asString()

                    ObjectRegistryEdit::class.react {
                        this.objectLocation = objectLocation
                        this.index = index
                        this.className = className

                        this.reflection = reflection?.getOrNull(index)
                    }

                    hr {}
                }
            }

            ObjectRegistryAdd::class.react {
                this.objectLocation = objectLocation
            }
        }
    }
}