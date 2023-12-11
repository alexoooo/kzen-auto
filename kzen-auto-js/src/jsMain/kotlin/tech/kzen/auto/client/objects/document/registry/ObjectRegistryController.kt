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
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.registry.ObjectRegistryConventions
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface ObjectRegistryControllerState: State {
    var objectLocation: ObjectLocation?
    var classes: ClassListSpec?
}


class ObjectRegistryController:
    RPureComponent<Props, ObjectRegistryControllerState>(),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ObjectRegistryControllerState.init(props: Props) {
        objectLocation = null
        classes = null
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

        setState {
            objectLocation = documentPath.toMainObjectLocation()
            classes = ObjectRegistryConventions.classesSpec(documentNotation)
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

            for (className in classes.classNames) {
                div {
                    key = className.asString()

//                    DataFormatFieldEdit::class.react {
//                        this.objectLocation = objectLocation
//                        this.fieldName = fieldName
//                        this.fieldSpec = fieldSpec
//                    }
                    +"Class name: $className"

                    hr {}
                }
            }

            ObjectRegistryAdd::class.react {
                this.objectLocation = objectLocation
            }
//            +"[objectLocation: $objectLocation]"
        }
    }
}