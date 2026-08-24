package tech.kzen.auto.common.objects.document.logic

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals


class StepValidationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun roundTrip(stepValidation: StepValidation): StepValidation {
        return StepValidation.ofMapExecutionValue(
            stepValidation.asExecutionValue() as MapExecutionValue)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun everyFieldSurvivesTheRoundTrip() {
        val stepValidation = StepValidation(
            TypeMetadata.unit, "Expecting an element", "Never runs", 6,
            HeaderListing.ofUnique(listOf("city", "amount")))

        assertEquals(stepValidation, roundTrip(stepValidation))
    }


    @Test
    fun absentValuesSurviveTheRoundTrip() {
        val stepValidation = StepValidation(null, null)

        assertEquals(stepValidation, roundTrip(stepValidation))
    }


    @Test
    fun offsetSurvivesTheJsonTransport() {
        // The offset crosses the wire as a number, so it makes the trip through JSON as a Double.
        val stepValidation = StepValidation(null, "Unresolved reference 'x'", errorOffset = 0)

        val decoded = ExecutionValue.fromJsonCollection(
            stepValidation.asExecutionValue().toJsonCollection())

        assertEquals(stepValidation, StepValidation.ofMapExecutionValue(decoded as MapExecutionValue))
    }


    @Test
    fun payloadWithoutTheOffsetKeyDecodes() {
        // Keys spelled out rather than read back from the encoder: this is the payload a peer that predates
        // the offset field writes, and it must still decode.
        val withoutOffset = MapExecutionValue(mapOf(
            "type" to NullExecutionValue,
            "error" to ExecutionValue.of("Expecting an element"),
            "warning" to NullExecutionValue))

        assertEquals(
            StepValidation(null, "Expecting an element"),
            StepValidation.ofMapExecutionValue(withoutOffset))
    }
}
