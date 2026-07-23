package tech.kzen.auto.common.objects.document.logic

import kotlinx.serialization.json.Json
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull


class ValidationDigestEchoTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun documentNotation(body: String): DocumentNotation {
        return DocumentNotation(YamlNotationParser().parseDocumentObjects(body), null)
    }


    private val main = documentNotation("""
main:
  is: Script
""")


    private fun echoed(documentNotation: DocumentNotation): ExecutionSuccess {
        return ExecutionSuccess
            .ofValue(MapExecutionValue(mapOf()))
            .withDetail(ValidationDigestEcho.detail(documentNotation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun detailCarriesTheDocumentDigest() {
        assertEquals(main.digest(), ValidationDigestEcho.ofDetail(ValidationDigestEcho.detail(main)))
    }


    @Test
    fun editedDocumentEchoesADifferentDigest() {
        val edited = documentNotation("""
main:
  is: Script
  summary: "edited"
""")

        assertEquals(edited.digest(), ValidationDigestEcho.ofDetail(ValidationDigestEcho.detail(edited)))
        assertNotEquals(main.digest(), edited.digest())
    }


    @Test
    fun absentDetailIsNotAnEcho() {
        assertNull(ValidationDigestEcho.ofDetail(NullExecutionValue))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun echoSurvivesTheJsonCollectionTransport() {
        // The server responds through toJsonCollection.
        val decoded = ExecutionResult.fromJsonCollection(echoed(main).toJsonCollection()) as ExecutionSuccess

        assertEquals(main.digest(), ValidationDigestEcho.ofDetail(decoded.detail))
    }


    @Test
    fun echoSurvivesTheSerializerTransport() {
        // The client decodes through the kotlinx serializer.
        val decoded = Json.decodeFromString<ExecutionResult>(
            Json.encodeToString<ExecutionResult>(echoed(main))) as ExecutionSuccess

        assertEquals(main.digest(), ValidationDigestEcho.ofDetail(decoded.detail))
    }
}
