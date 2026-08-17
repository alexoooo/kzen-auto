package tech.kzen.auto.common.paradigm.flow.model.structure.cell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Pins the ingress/egress table every walk over the cell grid rests on, and the two invariants that bound
 * those walks: exactly one ingress, and no egress by the ingress side.
 *
 * [sidesMatchTheVariantName] is the table check proper — it re-derives each variant's sides from its own name,
 * so a new variant declared with the wrong sides fails here instead of answering a quiet `false` somewhere.
 */
class EdgeOrientationTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sidesMatchTheVariantName() {
        for (orientation in EdgeOrientation.entries) {
            val ingress = EdgeDirection.entries.single { orientation.name.startsWith("${it.name}To") }
            val egress = orientation.name
                .removePrefix("${ingress.name}To")
                .split("And")
                .map { EdgeDirection.valueOf(it) }

            for (direction in EdgeDirection.entries) {
                assertEquals(
                    direction == ingress,
                    orientation.hasIngress(direction),
                    "$orientation ingress $direction")

                assertEquals(
                    direction in egress,
                    orientation.hasEgress(direction),
                    "$orientation egress $direction")
            }
        }
    }


    @Test
    fun flowEntersByExactlyOneSide() {
        for (orientation in EdgeOrientation.entries) {
            assertEquals(
                1,
                EdgeDirection.entries.count { orientation.hasIngress(it) },
                "$orientation")
        }
    }


    @Test
    fun flowNeverLeavesByTheSideItEntered() {
        for (orientation in EdgeOrientation.entries) {
            for (direction in EdgeDirection.entries) {
                assertFalse(
                    orientation.hasIngress(direction) && orientation.hasEgress(direction),
                    "$orientation $direction")
            }
        }
    }


    @Test
    fun everyOrientationLeadsSomewhere() {
        for (orientation in EdgeOrientation.entries) {
            assertTrue(
                EdgeDirection.entries.any { orientation.hasEgress(it) },
                "$orientation")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun topIsOnlyEverAnIngressAndBottomOnlyAnEgress() {
        for (orientation in EdgeOrientation.entries) {
            assertFalse(orientation.hasEgress(EdgeDirection.Top), "$orientation")
            assertFalse(orientation.hasIngress(EdgeDirection.Bottom), "$orientation")
        }
    }


    @Test
    fun namedPredicatesAgreeWithTheDirectionalOnes() {
        for (orientation in EdgeOrientation.entries) {
            assertEquals(orientation.hasIngress(EdgeDirection.Top), orientation.hasTop(), "$orientation")
            assertEquals(orientation.hasEgress(EdgeDirection.Bottom), orientation.hasBottom(), "$orientation")

            assertEquals(
                orientation.hasIngress(EdgeDirection.Left), orientation.hasLeftIngress(), "$orientation")
            assertEquals(
                orientation.hasEgress(EdgeDirection.Left), orientation.hasLeftEgress(), "$orientation")

            assertEquals(
                orientation.hasIngress(EdgeDirection.Right), orientation.hasRightIngress(), "$orientation")
            assertEquals(
                orientation.hasEgress(EdgeDirection.Right), orientation.hasRightEgress(), "$orientation")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun facingSidesAreWhatMakeARunConnected() {
        // The wiring test each lateral hop performs: one cell's egress meets the neighbour's ingress on the
        // facing side. A run of RightToLeft cells is therefore connected leftward and not rightward.
        assertTrue(EdgeOrientation.RightToLeft.hasEgress(EdgeDirection.Left))
        assertTrue(EdgeOrientation.RightToLeft.hasIngress(EdgeDirection.Left.reverse()))
        assertFalse(EdgeOrientation.RightToLeft.hasEgress(EdgeDirection.Right))
    }
}
