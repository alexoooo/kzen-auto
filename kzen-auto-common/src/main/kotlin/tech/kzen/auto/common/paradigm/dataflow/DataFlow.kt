package tech.kzen.auto.common.paradigm.dataflow


/**
 * Must inject FlowInput for use in the process method,
 *  can also inject more than one FlowInput or additional OptionalFlowInput.
 *
 * Can inject (at most one of) SingleFlowOutput or OptionalFlowOutput.
 *
 * See: https://en.wikipedia.org/wiki/Pipe_(fluid_conveyance)
 * See: https://en.wikipedia.org/wiki/Piping_and_plumbing_fitting
 */
interface DataFlow {
    /**
     * Make use of injected FlowInput (and OptionalFlowInput), plus any direct object references.
     *
     * Can also use injected SingleFlowOutput (or OptionalFlowOutput).
     */
    fun process()
}