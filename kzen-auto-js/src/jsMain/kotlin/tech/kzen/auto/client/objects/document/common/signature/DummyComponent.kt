package tech.kzen.auto.client.objects.document.common.signature

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import web.cssom.em
import web.cssom.minus
import web.cssom.pct


//---------------------------------------------------------------------------------------------------------------------
external interface DummyComponentProps: Props {
    var objectLocation: ObjectLocation
}


external interface DummyComponentState: State {
    var parameters: List<String>?
}


//---------------------------------------------------------------------------------------------------------------------
class DummyComponent:
    RPureComponent<DummyComponentProps, DummyComponentState>(),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun DummyComponentState.init(props: DummyComponentProps) {
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
        val graphNotation = clientState.graphStructure().graphNotation
        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val parametersNotation = graphNotation
            .firstAttribute(props.objectLocation, LogicConventions.parametersAttributeName)
                as? ListAttributeNotation
        val newParameters = parametersNotation?.values?.mapNotNull { i -> i.asString() }

        // NB: mapNotNull produces a fresh List reference each fire — guard with structural equality to keep
        //     RPureComponent's shallow state comparison from re-rendering on unchanged content.
        if (newParameters == state.parameters) {
            return
        }

        setState {
            parameters = newParameters
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            +"[${props.objectLocation} |z| ${state.parameters}]"
        }
    }
}