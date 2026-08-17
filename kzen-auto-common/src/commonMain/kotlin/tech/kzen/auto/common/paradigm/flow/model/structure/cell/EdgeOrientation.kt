package tech.kzen.auto.common.paradigm.flow.model.structure.cell


/**
 * The wiring of one edge cell: which side flow enters by, and which sides it leaves by.
 *
 * The name reads `<ingress>To<egress…>`, and the constructor states the same thing in a form the code can
 * read — so a new variant declares its sides rather than being added to the arm of several predicates, where
 * a missed arm would answer `false` silently.
 *
 * Two invariants, asserted per entry below because the walks over these cells rest on them
 * ([tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix]'s back-traces,
 * [tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag]'s forward trace): flow enters by **exactly
 * one** side, and never leaves by the side it entered. Together they are what bounds those walks — a lateral
 * hop can never immediately reverse, so travel within a row is monotonic in the column and every other hop
 * changes the row, over a finite grid.
 *
 * Top is only ever an ingress and Bottom only ever an egress: the layout flows downward, and a cell's lateral
 * sides are the only ones that can be either.
 */
enum class EdgeOrientation(
    private val ingress: EdgeDirection,
    vararg egress: EdgeDirection
) {
    TopToBottom(EdgeDirection.Top, EdgeDirection.Bottom),
    TopToLeft(EdgeDirection.Top, EdgeDirection.Left),
    TopToRight(EdgeDirection.Top, EdgeDirection.Right),
    TopToLeftAndRight(EdgeDirection.Top, EdgeDirection.Left, EdgeDirection.Right),
    TopToBottomAndLeft(EdgeDirection.Top, EdgeDirection.Bottom, EdgeDirection.Left),
    TopToBottomAndRight(EdgeDirection.Top, EdgeDirection.Bottom, EdgeDirection.Right),
    TopToBottomAndLeftAndRight(
        EdgeDirection.Top, EdgeDirection.Bottom, EdgeDirection.Left, EdgeDirection.Right),

    LeftToRight(EdgeDirection.Left, EdgeDirection.Right),
    LeftToBottom(EdgeDirection.Left, EdgeDirection.Bottom),
    LeftToRightAndBottom(EdgeDirection.Left, EdgeDirection.Right, EdgeDirection.Bottom),

    RightToLeft(EdgeDirection.Right, EdgeDirection.Left),
    RightToBottom(EdgeDirection.Right, EdgeDirection.Bottom),
    RightToLeftAndBottom(EdgeDirection.Right, EdgeDirection.Left, EdgeDirection.Bottom);


    private val egressSides: Set<EdgeDirection> = egress.toSet()


    init {
        check(ingress != EdgeDirection.Bottom) { "Bottom is an egress only: $ingress -> $egressSides" }
        check(EdgeDirection.Top !in egressSides) { "Top is an ingress only: $ingress -> $egressSides" }
        check(ingress !in egressSides) { "Egress may not re-use the ingress: $ingress -> $egressSides" }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun hasIngress(direction: EdgeDirection): Boolean {
        return direction == ingress
    }


    fun hasEgress(direction: EdgeDirection): Boolean {
        return direction in egressSides
    }


    fun hasTop(): Boolean {
        return hasIngress(EdgeDirection.Top)
    }


    fun hasBottom(): Boolean {
        return hasEgress(EdgeDirection.Bottom)
    }


    fun hasLeftIngress(): Boolean {
        return hasIngress(EdgeDirection.Left)
    }


    fun hasLeftEgress(): Boolean {
        return hasEgress(EdgeDirection.Left)
    }


    fun hasRightIngress(): Boolean {
        return hasIngress(EdgeDirection.Right)
    }


    fun hasRightEgress(): Boolean {
        return hasEgress(EdgeDirection.Right)
    }
}
