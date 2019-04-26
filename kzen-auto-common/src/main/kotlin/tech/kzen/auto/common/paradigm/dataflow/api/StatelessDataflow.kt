package tech.kzen.auto.common.paradigm.dataflow.api


interface StatelessDataflow:
        Dataflow<Unit>
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