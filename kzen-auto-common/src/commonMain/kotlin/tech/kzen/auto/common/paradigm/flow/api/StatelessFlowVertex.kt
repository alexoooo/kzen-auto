package tech.kzen.auto.common.paradigm.flow.api


interface StatelessFlowVertex
    : FlowVertex<Unit>
{
    override fun initialState() {}


    override fun inspectState(state: Unit): Nothing {
        throw UnsupportedOperationException()
    }


    override fun process(state: Unit) {
        process()
    }


    fun process()
}