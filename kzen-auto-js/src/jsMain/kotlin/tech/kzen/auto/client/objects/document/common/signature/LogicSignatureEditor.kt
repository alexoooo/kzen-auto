package tech.kzen.auto.client.objects.document.common.signature

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.common.v1.model.LogicConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation


//---------------------------------------------------------------------------------------------------------------------
external interface LogicSignatureEditorProps: Props {
    var objectLocation: ObjectLocation
}


external interface LogicSignatureEditorState: State {
    var parameters: List<String>?
}


//---------------------------------------------------------------------------------------------------------------------
class LogicSignatureEditor:
    RPureComponent<LogicSignatureEditorProps, LogicSignatureEditorState>(),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun LogicSignatureEditorState.init(props: LogicSignatureEditorProps) {
        parameters = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val parametersNotation = clientState
            .graphStructure()
            .graphNotation
            .firstAttribute(props.objectLocation, LogicConventions.parametersAttributeName)
            as? ListAttributeNotation

        setState {
            parameters = parametersNotation?.values?.mapNotNull { i -> i.asString() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        +"[Parameters: ${state.parameters}]"
    }
}