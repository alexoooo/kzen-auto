package tech.kzen.auto.common.paradigm.logic


/**
 * The wording of the move-to (Set Next Statement) refusals that BOTH sides can raise, in one place because both
 * do: the server refuses a request it received, and the client refuses a drag it can rule out from the same
 * notation without asking. Two copies of one sentence would drift, and the user would meet both.
 *
 * Each reads as the detail line under "Can't move to this step", so it names the document at fault and what
 * that document can't do — never how the engine is built. A refusal only one side can reach stays where it is
 * raised; this holds the shared ones alone.
 */
object MoveToRefusal {
    // A frame the run passes through is of a flavour that can't reposition at all.
    fun frameCannotReposition(documentName: String): String {
        return "This run passes through $documentName, which doesn't support setting the next step"
    }


    // A frame the run passes through names no call-site for the frame it hosts, so the frame beyond it can't be
    // addressed at all.
    fun frameCallSiteUnknown(documentName: String): String {
        return "This run passes through $documentName, which doesn't record where it called the nested logic"
    }


    // A frame the run passes through can't re-establish its walk at the call-site it hosts the next frame from.
    fun frameCannotResume(documentName: String, stepName: String): String {
        return "This run passes through $documentName, which can't resume at its $stepName step"
    }


    fun frameDocumentMissing(): String {
        return "A document this run passes through no longer exists"
    }


    // A destination inside a loop body. The run's walk can be re-pointed but the loop's iteration cursor can't,
    // so there is no iteration for the step to land in. Naming the way out is the point of the second sentence:
    // the loop step ITSELF is a legal destination, and restarting the loop is what the user usually meant.
    fun targetInsideLoop(loopName: String): String {
        return "That step is inside $loopName, which can't be sent to a different iteration. " +
                "Move to $loopName itself to restart it."
    }


    // A destination the run's walk never visits — a binding, a branch heading, or an element that isn't part of
    // this document at all.
    fun targetNotJumpable(): String {
        return "That isn't a step this Script can jump to"
    }
}
