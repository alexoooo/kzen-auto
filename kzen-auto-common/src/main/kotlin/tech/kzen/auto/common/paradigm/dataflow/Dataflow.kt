package tech.kzen.auto.common.paradigm.dataflow


/**
 * Must inject RequiredIngress for use in the process method,
 *  can also inject more than one RequiredIngress or additional OptionalIngress.
 *
 * Can inject (at most one of) RequiredEgress or OptionalEgress.
 *
 * See: https://en.wikipedia.org/wiki/Pipe_(fluid_conveyance)
 * See: https://en.wikipedia.org/wiki/Piping_and_plumbing_fitting
 */
interface Dataflow {
    /**
     * Make use of injected RequiredIngress (and OptionalIngress), plus any direct object references.
     *
     * Can also use injected RequiredEgress (or OptionalEgress).
     */
    fun process()
}