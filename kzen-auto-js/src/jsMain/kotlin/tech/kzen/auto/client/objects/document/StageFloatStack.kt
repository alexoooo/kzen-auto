package tech.kzen.auto.client.objects.document

import csstype.PropertiesBuilder
import web.cssom.Position
import web.cssom.em
import web.cssom.integer


// Geometry of the stack: where the first row starts and how far apart the rows sit. Every row also clears the
// chip row StageErrorIndicator reserves unconditionally.
private const val firstRowTopEm = 0.5
private const val rowHeightEm = 2.25
private const val rightEm = 0.5

// How much horizontal room the stack occupies: the widest member (a Job's Channel defaults column, 11em) plus the
// corner offset and a little clearance. Held here beside the offsets it has to agree with.
private const val gutterEm = 12.5


/**
 * The stage's top-right float stack: the document-level controls (Parameters, Result, Requires / Provides, a
 * Job's Channel defaults) that hang over the top-right corner of the stage rather than flowing in it, so a
 * document declaring none of them costs no vertical space and its body still starts at the top.
 *
 * Each member declares only WHICH row it occupies, and holds that declaration here. That is the point of the
 * shared registry — the offsets were once hardcoded per member and had to agree by inspection, which they
 * eventually did not: a member that left out the reserved error-chip row overlapped the one above it.
 *
 * A Script's stack and a Job's share the first two rows and diverge below, so row 2 is claimed twice — by
 * members no single document ever mounts together. The row is just an `Int`, so a document type can hold its
 * own; listing them together is what makes a genuine collision visible at all.
 */
object StageFloatStack {
    const val parametersRow = 0
    const val resultRow = 1
    const val contextRequiresRow = 2
    const val contextProvidesRow = 3
    const val jobChannelDefaultsRow = 2
}


/**
 * Positions the receiver as float [row] of the stack (0-based, top down). The caller still owns its own layout —
 * only the corner anchoring is shared.
 */
fun PropertiesBuilder.stageFloatRow(row: Int) {
    position = Position.absolute
    top = (firstRowTopEm + rowHeightEm * row + StageErrorIndicator.reservedRowEm).em
    right = rightEm.em
    // above the step cards (which are positioned but auto z-index) so it stays clickable.
    zIndex = integer(2)
}


/**
 * Keeps a stage's content clear of its float stack, applied to the positioned stage container itself.
 *
 * Hanging over the corner costs no vertical space, but it costs horizontal space that nothing was reserving: on a
 * narrow window the stack sat ON the first card, and being the only thing there with a z-index it swallowed that
 * card's clicks — its Delete button included. An absolutely positioned element resolves its offsets against its
 * ancestor's PADDING box, so this padding does not move the stack a pixel; it only stops the content growing under
 * it, and the cards narrow instead of being covered.
 */
fun PropertiesBuilder.stageFloatGutter() {
    paddingRight = gutterEm.em
}
